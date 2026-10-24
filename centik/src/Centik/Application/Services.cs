using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices.WindowsRuntime;
using System.Threading.Tasks;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Microsoft.Win32;
using NAudio.CoreAudioApi;
using Windows.Media.Control;

namespace Centik;

public sealed class MediaService : IDisposable
{
    private GlobalSystemMediaTransportControlsSessionManager? manager;
    private GlobalSystemMediaTransportControlsSession? selected;
    private readonly System.Windows.Threading.Dispatcher dispatcher;
    private readonly Action<Exception> log;
    private bool disposed;
    private int generation;
    private string artworkKey="";
    private long timelineStamp;
    private (double elapsed,double duration) timelineSample;
    private string? preferredSession;
    public event Action? Changed;
    public List<GlobalSystemMediaTransportControlsSession> Sessions { get; private set; }=new();
    public GlobalSystemMediaTransportControlsSession? Selected=>selected;
    public static string SessionName(GlobalSystemMediaTransportControlsSession session)
    {
        try {return Windows.ApplicationModel.AppInfo.GetFromAppUserModelId(session.SourceAppUserModelId).DisplayInfo.DisplayName;}
        catch {var id=session.SourceAppUserModelId;if(id.Contains("Chrome",StringComparison.OrdinalIgnoreCase))return "Google Chrome";return Path.GetFileNameWithoutExtension(id);}
    }
    public string Title { get; private set; }="Hiçbir şey çalmıyor";
    public string Artist { get; private set; }="Sevdiğiniz uygulamada bir şey çalın";
    public ImageSource? Artwork { get; private set; }
    public bool Playing { get; private set; }
    internal bool DiagnosticFixture {get;set;}
    internal void DiagnosticPlayback(bool playing){if(!DiagnosticFixture)throw new InvalidOperationException();Playing=playing;Title="Performance fixture";Artist="Deterministic local signal";CanToggle=CanSeek=true;Changed?.Invoke();}
    public bool Available=>DiagnosticFixture || selected!=null;
    public bool CanToggle { get; private set; }
    public bool CanNext { get; private set; }
    public bool CanPrevious { get; private set; }
    public bool CanSeek { get; private set; }
    public MediaService(Action<Exception> log)
    { dispatcher=System.Windows.Threading.Dispatcher.CurrentDispatcher; this.log=log; }
    public async Task Initialize()
    {
        try
        {
            manager=await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
            manager.SessionsChanged+=SessionsChanged; manager.CurrentSessionChanged+=CurrentChanged;
            Reload();
        }
        catch(Exception ex) { log(ex); Artist="Medya denetimleri kullanılamıyor"; Changed?.Invoke(); }
    }
    private void SessionsChanged(GlobalSystemMediaTransportControlsSessionManager sender,SessionsChangedEventArgs args)=>dispatcher.BeginInvoke(Reload);
    private void CurrentChanged(GlobalSystemMediaTransportControlsSessionManager sender,CurrentSessionChangedEventArgs args)=>dispatcher.BeginInvoke(()=>{if(preferredSession==null)Select(manager?.GetCurrentSession(),false);});
    private void Reload()
    {
        if(disposed || manager==null) return;
        Sessions=manager.GetSessions().ToList();
        if(preferredSession!=null && !Sessions.Any(s=>s.SourceAppUserModelId==preferredSession))preferredSession=null;
        Select(Sessions.FirstOrDefault(s=>s.SourceAppUserModelId==(preferredSession??selected?.SourceAppUserModelId)) ?? manager.GetCurrentSession(),false);
    }
    public void Select(GlobalSystemMediaTransportControlsSession? session,bool manual=true)
    {
        if(disposed) return;
        if(manual)preferredSession=session?.SourceAppUserModelId;
        if(ReferenceEquals(selected,session)){Changed?.Invoke();return;}
        if(selected!=null) { selected.MediaPropertiesChanged-=PropertiesChanged; selected.PlaybackInfoChanged-=PlaybackChanged;selected.TimelinePropertiesChanged-=TimelineChanged; }
        selected=session;timelineStamp=0;
        if(selected!=null) { selected.MediaPropertiesChanged+=PropertiesChanged; selected.PlaybackInfoChanged+=PlaybackChanged;selected.TimelinePropertiesChanged+=TimelineChanged; }
        _=Refresh();
    }
    private void PropertiesChanged(GlobalSystemMediaTransportControlsSession sender,MediaPropertiesChangedEventArgs args)=>dispatcher.BeginInvoke(()=>_=Refresh());
    private void PlaybackChanged(GlobalSystemMediaTransportControlsSession sender,PlaybackInfoChangedEventArgs args)=>dispatcher.BeginInvoke(()=>{if(!disposed && selected!=null && sender.SourceAppUserModelId==selected.SourceAppUserModelId){ReadPlayback(selected);Changed?.Invoke();}});
    private void TimelineChanged(GlobalSystemMediaTransportControlsSession sender,TimelinePropertiesChangedEventArgs args)=>dispatcher.BeginInvoke(()=>{if(!disposed && selected!=null && selected.SourceAppUserModelId==sender.SourceAppUserModelId)timelineStamp=0;});
    private void ReadPlayback(GlobalSystemMediaTransportControlsSession session)
    {
        timelineStamp=0;
        try{var playback=session.GetPlaybackInfo();var controls=playback?.Controls;
        Playing=playback?.PlaybackStatus==GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
        CanToggle=controls?.IsPlayPauseToggleEnabled==true;CanNext=controls?.IsNextEnabled==true;CanPrevious=controls?.IsPreviousEnabled==true;CanSeek=controls?.IsPlaybackPositionEnabled==true;}
        catch(Exception ex){log(ex);Playing=false;CanToggle=CanNext=CanPrevious=CanSeek=false;}
    }
    public async Task Refresh()
    {
        var version=++generation; var session=selected;
        try
        {
            if(session==null) { artworkKey="";Title="Hiçbir şey çalmıyor";Artist="Sevdiğiniz uygulamada bir şey çalın";Playing=false;Artwork=null;CanToggle=CanNext=CanPrevious=CanSeek=false;Changed?.Invoke();return; }
            var p=await session.TryGetMediaPropertiesAsync();
            if(disposed || version!=generation)return;
            if(p==null){Playing=false;CanToggle=CanNext=CanPrevious=CanSeek=false;Changed?.Invoke();return;}
            string key=session.SourceAppUserModelId+"\n"+p.Title+"\n"+p.Artist+"\n"+p.AlbumTitle;
            ImageSource? artwork=key==artworkKey?Artwork:null;
            if(p.Thumbnail!=null && artwork==null)
            {
                try
                {
                    using var stream=await p.Thumbnail.OpenReadAsync();
                    if(stream.Size>0 && stream.Size<=10*1024*1024)
                    {
                        using var managed=stream.AsStreamForRead();
                        var bitmap=new BitmapImage();bitmap.BeginInit();bitmap.CacheOption=BitmapCacheOption.OnLoad;bitmap.DecodePixelWidth=160;
                        bitmap.StreamSource=managed;bitmap.EndInit();bitmap.Freeze();artwork=bitmap;
                    }
                }
                catch(Exception ex) when(ex is System.Runtime.InteropServices.COMException or IOException or NotSupportedException or ArgumentException) { /* Artwork is optional; playback metadata and commands remain usable. */ }
            }
            if(disposed || version!=generation) return;
            Title=string.IsNullOrWhiteSpace(p.Title) ? "Adsız medya" : p.Title;
            Artist=string.IsNullOrWhiteSpace(p.Artist) ? session.SourceAppUserModelId : p.Artist;
            ReadPlayback(session);
            artworkKey=key;Artwork=artwork;Changed?.Invoke();
        }
        catch(Exception ex) { if(disposed || version!=generation)return;log(ex);Artist="Medya oturumu kullanılamıyor";CanToggle=CanNext=CanPrevious=CanSeek=false;Changed?.Invoke(); }
    }
    public async Task Command(string command)
    {
        try
        {
            if(selected==null) return;
            bool ok=command switch { "next" when CanNext=>await selected.TrySkipNextAsync(), "previous" when CanPrevious=>await selected.TrySkipPreviousAsync(), "toggle" when CanToggle=>await selected.TryTogglePlayPauseAsync(), _=>false };
            if(!ok) Artist="Bu medya uygulaması bu denetimi desteklemiyor";
            Changed?.Invoke();
        }
        catch(Exception ex) { log(ex); Artist="Medya denetimi başarısız oldu; oturumu yeniden seçmeyi deneyin";Changed?.Invoke(); }
    }
    public (double elapsed,double duration) Timeline()
    {
        long now=Stopwatch.GetTimestamp();
        if(timelineStamp==0 || Stopwatch.GetElapsedTime(timelineStamp,now).TotalSeconds>=1){timelineSample=ReadTimeline();timelineStamp=now;}
        double elapsed=timelineSample.elapsed+(Playing?Stopwatch.GetElapsedTime(timelineStamp,now).TotalSeconds:0);
        return(Math.Clamp(elapsed,0,Math.Max(0,timelineSample.duration)),timelineSample.duration);
    }
    private (double elapsed,double duration) ReadTimeline()
    {
        if(DiagnosticFixture)return(Playing?Environment.TickCount64/1000d%240:40,240);
        try
        {
            if(selected==null)return(0,0);var timeline=selected.GetTimelineProperties();
            double duration=(timeline.EndTime-timeline.StartTime).TotalSeconds;
            if(duration<=0)return(0,0);
            double position=(timeline.Position-timeline.StartTime).TotalSeconds;
            double age=(DateTimeOffset.UtcNow-timeline.LastUpdatedTime).TotalSeconds;
            if(Playing && age>=0 && age<86400)position+=age;
            return(Math.Clamp(position,0,duration),duration);
        }
        catch{return(0,0);}
    }
    public async Task Seek(double fraction)
    {
        try
        {
            if(selected==null || !CanSeek)return;
            var timeline=selected.GetTimelineProperties();if(timeline==null)return;var span=timeline.EndTime-timeline.StartTime;if(span<=TimeSpan.Zero)return;
            long ticks=timeline.StartTime.Ticks+(long)(span.Ticks*Math.Clamp(fraction,0,1));
            timelineStamp=0;bool changed=await selected.TryChangePlaybackPositionAsync(ticks);timelineStamp=0;if(!changed){Artist="Bu uygulama ileri/geri sarmayı desteklemiyor";Changed?.Invoke();}
        }
        catch(Exception ex){log(ex);}
    }
    public void Dispose()
    {
        disposed=true;generation++;
        if(manager!=null) { manager.SessionsChanged-=SessionsChanged;manager.CurrentSessionChanged-=CurrentChanged; }
        if(selected!=null) { selected.MediaPropertiesChanged-=PropertiesChanged;selected.PlaybackInfoChanged-=PlaybackChanged;selected.TimelinePropertiesChanged-=TimelineChanged; }
    }
}

public sealed partial class AudioService : IDisposable
{
    private MMDeviceEnumerator? enumerator;
    private MMDeviceEnumerator EndpointEnumerator=>enumerator??=new MMDeviceEnumerator();
    private MMDevice? meterDevice;
    private PeakCache<MMDevice>? peakCache;
    private readonly System.Windows.Threading.Dispatcher dispatcher=System.Windows.Threading.Dispatcher.CurrentDispatcher;
    private bool disposed;
    private int endpointGeneration;
    private readonly Action<Exception>? log;
    private string? lastLoggedError;
    public string? LastError { get; private set; }
    public AudioService(Action<Exception>? log=null) { this.log=log; }
    internal void AudioError(Exception error,AudioOperation operation=AudioOperation.ReadOutput)
    {
        LastError=error is ObjectDisposedException
            ? "Ses hizmeti kapandı. Ses denetimlerini kullanmak için BillyNabila Island'ı yeniden açın."
            : operation switch
            {
                AudioOperation.ListOutputs=>"Ses çıkışları okunamadı. Aygıtınızı yeniden bağlayın, ardından Windows Ses Ayarları'nı açın.",
                AudioOperation.SetOutput=>"Ses çıkışı değiştirilemedi. Etkin bir aygıt seçin veya çıkışı Windows Ses Ayarları'ndan değiştirin.",
                AudioOperation.SetVolume=>"Ses düzeyi değiştirilemedi. Çıkış aygıtınızı yeniden bağlayın veya ses düzeyini Windows Ses Ayarları'ndan ayarlayın.",
                AudioOperation.ToggleMute=>"Çıkış sessize alınamadı. Etkin bir aygıt seçip Windows Ses Ayarları'ndan yeniden deneyin.",
                AudioOperation.ReadMicrophone=>"Mikrofon okunamadı. Yeniden bağlayın ve Windows Ses Ayarları'ndan bir giriş seçin.",
                AudioOperation.ToggleMicrophoneMute=>"Mikrofon sessize alınamadı. Yeniden bağlayın veya Windows Ses Ayarları'ndan bir giriş seçin.",
                _=>"Ses aygıtı okunamadı. Yeniden bağlayın ve Windows Ses Ayarları'ndan bir çıkış seçin."
            };
        if(lastLoggedError==error.Message)return;
        lastLoggedError=error.Message;
        try { if(log!=null)log(error);else Trace.TraceError(error.ToString()); } catch { /* A logger must not break audio controls. */ }
    }
    private bool CheckAudioActive(AudioOperation operation=AudioOperation.ReadOutput)
    {
        if(!disposed)return true;
        AudioError(new ObjectDisposedException(nameof(AudioService),"The audio service has closed."),operation);return false;
    }
    public event Action? Changed;
    private void ReleaseMeter()
    {
        endpointGeneration++;
        var old=meterDevice;meterDevice=null;
        if(old!=null)
        {
            try{old.AudioEndpointVolume.OnVolumeNotification-=VolumeChanged;}catch(Exception ex){AudioError(ex);}
            try{old.Dispose();}catch(Exception ex){AudioError(ex);}
        }
    }
    private void VolumeChanged(AudioVolumeNotificationData data)
    {
        int version=endpointGeneration;
        if(disposed || dispatcher.HasShutdownStarted)return;
        try{dispatcher.BeginInvoke(()=>{if(disposed || version!=endpointGeneration)return;Volume=data.MasterVolume;Muted=data.Muted;Changed?.Invoke();});}
        catch(Exception ex){AudioError(ex);}
    }
    internal float Peak()
    {
        if(disposed)return 0;
        peakCache??=new PeakCache<MMDevice>(()=>EndpointEnumerator.EnumerateAudioEndPoints(DataFlow.Render,DeviceState.Active).ToArray(),
            d=>d.AudioEndpointVolume.Mute?0:d.AudioMeterInformation.MasterPeakValue,d=>d.Dispose(),ex=>AudioError(ex));
        return peakCache.Read(DateTimeOffset.UtcNow);
    }
    public bool Available { get; private set; }
    public float Volume { get; private set; }
    public bool Muted { get; private set; }
    public string Device { get; private set; }="Ses aygıtı yok";
    // Acquire the current default endpoint every time: unplugging or changing devices cannot leave a stale COM endpoint.
    public void Read()
    {
        if(!CheckAudioActive())return;
        LastError=null;EnsureDeviceNotifications();
        MMDevice? current=null;
        try
        {
            current=EndpointEnumerator.GetDefaultAudioEndpoint(DataFlow.Render,Role.Multimedia);
            if(meterDevice==null || meterDevice.ID!=current.ID)
            {
                ReleaseMeter();meterDevice=current;current=null;
                meterDevice.AudioEndpointVolume.OnVolumeNotification+=VolumeChanged;
            }
            Volume=meterDevice!.AudioEndpointVolume.MasterVolumeLevelScalar;Muted=meterDevice.AudioEndpointVolume.Mute;
            Device=meterDevice.FriendlyName;DefaultOutputId=meterDevice.ID;Available=true;
        }
        catch(Exception ex){AudioError(ex);ReleaseMeter();Available=false;Volume=0;Muted=false;DefaultOutputId="";Device="Ses aygıtı yok";}
        finally{try{current?.Dispose();}catch(Exception ex){AudioError(ex);}}
        ReadMicrophone();nextAudioAudit=Environment.TickCount64+(notifications!=null && Available?60000:2000);
    }
    public void SetVolume(double value)
    {
        if(!CheckAudioActive(AudioOperation.SetVolume))return;
        try { using var d=EndpointEnumerator.GetDefaultAudioEndpoint(DataFlow.Render,Role.Multimedia);d.AudioEndpointVolume.MasterVolumeLevelScalar=(float)Math.Clamp(value,0,1);LastError=null; }
        catch(Exception ex){AudioError(ex,AudioOperation.SetVolume);Available=false;}
    }
    public void ToggleMute()
    {
        if(!CheckAudioActive(AudioOperation.ToggleMute))return;
        try { using var d=EndpointEnumerator.GetDefaultAudioEndpoint(DataFlow.Render,Role.Multimedia);d.AudioEndpointVolume.Mute=!d.AudioEndpointVolume.Mute;Read(); }
        catch(Exception ex){AudioError(ex,AudioOperation.ToggleMute);Available=false;}
    }
    public void Dispose()
    {
        if(disposed)return;
        disposed=true;outputSpectrum?.Dispose();outputSpectrum=null;MediaSpectrum=null;DisposeNotifications();ReleaseMeter();mediaPeaks?.Dispose();mediaPeaks=null;peakCache?.Dispose();peakCache=null;
        try{enumerator?.Dispose();}catch(Exception ex){AudioError(ex);}
        enumerator=null;Available=MicrophoneAvailable=false;Volume=0;Muted=MicrophoneMuted=false;DefaultOutputId="";
    }
}

public static class StartupService
{
    private const string Key=@"Software\Microsoft\Windows\CurrentVersion\Run";
    public static bool Enabled()
    { using var key=Registry.CurrentUser.OpenSubKey(Key); return key?.GetValue("Centik")!=null || key?.GetValue("BillyNabilaIsland")!=null || key?.GetValue("LumaIsland")!=null; }
    public static void Set(bool enabled)
    {
        using var key=Registry.CurrentUser.CreateSubKey(Key);
        if(enabled) key.SetValue("Centik","\""+Environment.ProcessPath+"\" --background");
        else key.DeleteValue("Centik",false);
        key.DeleteValue("BillyNabilaIsland",false);
        key.DeleteValue("LumaIsland",false);
    }
}
public static class ShortcutService
{
    internal static ProcessStartInfo InstalledAppStartInfo(string appId)
    {
        if(!WindowsAppCatalog.ValidAppId(appId))throw new ArgumentException("Bu kurulu uygulama kısayolu geçersiz. Kaldırın ve uygulamayı Ayarlar'dan yeniden ekleyin.");
        var info=new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Windows),"explorer.exe")){UseShellExecute=false};
        info.ArgumentList.Add("shell:AppsFolder\\"+appId);return info;
    }
    private static void OpenInstalledApp(string appId)
    {
        var info=InstalledAppStartInfo(appId);object? shell=null,folder=null,item=null;
        try
        {
            shell=Activator.CreateInstance(Type.GetTypeFromProgID("Shell.Application")!);folder=((dynamic)shell!).NameSpace("shell:AppsFolder");
            item=folder==null?null:((dynamic)folder).ParseName(appId);
            if(item==null)throw new FileNotFoundException("Bu uygulama artık kurulu değil. Kısayolu kaldırın ve Ayarlar'dan kurulu bir uygulama seçin.");
        }
        finally{ShellWorker.Release(item);ShellWorker.Release(folder);ShellWorker.Release(shell);}
        Process.Start(info);
    }

    public static void Open(Shortcut shortcut)
    {
        if(shortcut.AppId!=null){OpenInstalledApp(shortcut.AppId);return;}
        var target=Environment.ExpandEnvironmentVariables(shortcut.Target.Trim());
        if(string.IsNullOrWhiteSpace(target)) throw new ArgumentException("Kısayol hedefi boş.");
        if(Uri.TryCreate(target,UriKind.Absolute,out var uri) && !uri.IsFile)
        {
            if(uri.Scheme is not ("http" or "https" or "ms-settings" or "spotify" or "mailto"))
                throw new ArgumentException("Bir dosya, klasör, uygulama veya http/https URL'si kullanın.");
        }
        else if((Path.IsPathRooted(target) || target.Contains('\\')) && !File.Exists(target) && !Directory.Exists(target))
            throw new FileNotFoundException("Kısayol dosyası veya klasörü bulunamadı.");
        Process.Start(new ProcessStartInfo(target){UseShellExecute=true});
    }
}
