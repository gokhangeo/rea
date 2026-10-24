using System;
using System.Windows;
using System.Windows.Controls;

namespace Centik;

public sealed partial class SettingsWindow
{
    private void MediaExperienceSettings()
    {
        var s=app.Settings;var mode=new ComboBox{ItemsSource=new[]{"Frequency spectrum","Amplitude history"},SelectedIndex=s.WaveformSource=="Spectrum"?0:1};UI.Named(mode,"Visualizer input");
        mode.SelectionChanged+=(_,_)=>{s.WaveformSource=mode.SelectedIndex==0?"Spectrum":"Envelope";Save();};
        var sensitivity=new StackPanel();AddSlider(sensitivity,"Visualizer sensitivity",s.WaveformSensitivity,.5,3,v=>s.WaveformSensitivity=v,"×",.05);
        page.Children.Add(Section("Visualizer",mode,sensitivity,UI.Text("Spektrum; seçili medya uygulamasının etkin çıkışından gerçek bas, orta ve tiz ayırır. Bu çıkışı paylaşan diğer uygulamalar bantları etkileyebilir. Seçili uygulamayı susturmak gösterimi sessizleştirir. Çıkış örnekleri bellekte kalır; mikrofon yakalama asla kullanılmaz. Desteklenmeyen çıkış biçimlerinde genlik geçmişi yedek olarak kalır.",11,UI.Muted)));
        var presets=new ComboBox{ItemsSource=new[]{"Slide · flowing lines","Focus · soft emphasis","Karaoke · line sweep"},SelectedIndex=Array.IndexOf(new[]{"Slide","Focus","Karaoke"},s.LyricsAnimation)};UI.Named(presets,"Lyrics animation preset");presets.SelectionChanged+=(_,_)=>{if(presets.SelectedIndex<0)return;s.LyricsAnimation=new[]{"Slide","Focus","Karaoke"}[presets.SelectedIndex];Save();};
        var timing=new StackPanel();AddSlider(timing,"Lyrics timing offset",s.LyricsOffset,-10,10,v=>s.LyricsOffset=v," s",.1);var local=UI.Text("Hizmet kullanılamadığında yerel LRC dosyaları zamanlama sağlayabilir. İçe aktarmalar bu uygulama oturumu boyunca geçerlidir.",11,UI.Muted);timing.Children.Add(UI.Button("Yerel zamanlı şarkı sözü yükle…",()=>local.Text=LyricsFiles.Import(app,this)));timing.Children.Add(local);
        page.Children.Add(Section("Lyrics",LyricsAnimationExample(),Check("Show synced Spotify lyrics",s.LyricsEnabled,v=>s.LyricsEnabled=v),UI.Text("Varsayılan olarak kapalı. Görünürken; şarkı adı, sanatçı ve süre, zamanlı şarkı sözü bulmak için LRCLIB'e gönderilir. Spotify girişi ya da API anahtarı gerekmez. Erişilebilirlik parçaya göre değişir; şarkı sözleri yalnızca bellekte önbelleğe alınır.",11,UI.Muted),presets,timing,UI.Text("Oynatma denetimlerinin yanındaki şarkı sözü simgesi bu tercihi açıp kapatır. Kaydır her yeni satırı izler; Odak satırı hafifçe vurgular; Karaoke satır zamanlamasını kullanarak satır boyunca ilerler. Hareketi Azalt metni sabit tutar. Şarkı sözü düğmesine Tab ile gidip göstermek/gizlemek için Space'e basın.",11,UI.Muted)));
    }
    private FrameworkElement LyricsAnimationExample()
    {
        var sample=new LyricResult(new[]{new LyricLine(0,"A little closer to the music"),new LyricLine(4,"One line, right on time"),new LyricLine(8,"Let the next moment arrive")},"Animation preview · original sample text");
        var view=new LyricsView(app){Height=124,Margin=new(0,0,0,14)};view.SetFixture(sample,5.5,12);
        var timer=new System.Windows.Threading.DispatcherTimer{Interval=TimeSpan.FromMilliseconds(100)};var clock=System.Diagnostics.Stopwatch.StartNew();
        void Sync(){if(ElementVisibility.InViewport(view) && !app.Settings.ReduceMotion && Motion.Enabled)timer.Start();else timer.Stop();view.SetFixture(sample,5.5,12);}
        ScrollChangedEventHandler scrolled=(_,_)=>Sync();EventHandler state=(_,_)=>Sync();
        timer.Tick+=(_,_)=>{if(!ElementVisibility.InViewport(view)){timer.Stop();return;}view.SetFixture(sample,clock.Elapsed.TotalSeconds%12,12);};view.Loaded+=(_,_)=>{app.SettingsChanged+=Sync;settingsScroll.ScrollChanged+=scrolled;StateChanged+=state;Sync();};view.Unloaded+=(_,_)=>{app.SettingsChanged-=Sync;settingsScroll.ScrollChanged-=scrolled;StateChanged-=state;timer.Stop();};view.IsVisibleChanged+=(_,_)=>Sync();view.SizeChanged+=(_,_)=>Sync();return view;
    }
    private void DockEditingSettings()
    {
        page.Children.Add(Section("Move & resize",new DockPositionEditor(app,Save),Check("Show island move and resize handles",app.Settings.DockUnlocked,value=>app.Settings.DockUnlocked=value),UI.Text("Adayı yeniden konumlandırmak için üstteki tutamacı sürükleyin. Geçerli durumu (kapalı, üzerine gelme veya açık panel) yeniden boyutlandırmak için alt köşelerden birini sürükleyin. Konum ve her boyut ayrı ayrı kaydedilir. Tutamaçları geçici olarak göstermek için ada odaktayken Alt'ı basılı tutun; Esc sürüklemeyi iptal eder. Aşağı sürüklemek çentiği bir hap şekline ayırır; çentik olarak başlayan bir sürükleme üst kenara geri yapışabilir.",11,UI.Muted)));
    }
    private void CompletionSoundSettings()
    {
        var s=app.Settings;var sounds=new ComboBox{ItemsSource=new[]{"Chime","Bell","Custom","Off"},SelectedItem=s.CountdownSound};UI.Named(sounds,"Timer and Focus completion sound");
        var path=UI.Text(s.CountdownSoundFile.Length>0?System.IO.Path.GetFileName(s.CountdownSoundFile):"No custom sound chosen",11,UI.Muted);
        var volume=new StackPanel();AddSlider(volume,"Completion sound volume",s.CountdownSoundVolume*100,0,100,value=>s.CountdownSoundVolume=value/100,"%");
        sounds.SelectionChanged+=(_,_)=>{s.CountdownSound=sounds.SelectedItem?.ToString()??"Chime";Save();};
        var feedback=UI.Text("",11,UI.Muted);
        var choose=UI.Button("WAV seç…",()=>{var dialog=new Microsoft.Win32.OpenFileDialog{Filter="Wave audio (*.wav)|*.wav",Title="Choose a completion sound (up to 15 seconds)"};if(dialog.ShowDialog(this)!=true)return;s.CountdownSoundFile=dialog.FileName;s.CountdownSound="Custom";sounds.SelectedItem="Custom";path.Text=System.IO.Path.GetFileName(dialog.FileName);Save();});
        page.Children.Add(Section("Completion sound",sounds,volume,path,choose,UI.Button("Sesi önizle",()=>{app.CountdownAudio.Play(s);feedback.Text=app.CountdownAudio.LastError??"Played through your Windows output. Sound does not depend on Windows notifications.";}),feedback,UI.Text("Zamanlayıcı ve Odak, türe özel bildirim anahtarları açıkken bu sesi doğrudan çalar. Geçerli Windows çıkışını kullanır; Kapalı onu sessizleştirir. Özel WAV dosyaları 10 MB'tan küçük ve en çok 15 saniye olmalıdır. Dosya yolları yerelde tutulur ve yerleşim dışa aktarmalarına dahil edilmez.",11,UI.Muted)));
    }
}
