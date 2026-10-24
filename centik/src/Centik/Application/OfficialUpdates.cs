using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace Centik;

internal sealed record OfficialRelease(Version Version,string Tag,Uri Notes,Uri Setup,long Size,Uri Checksums);
internal sealed class UpdateState { public DateTimeOffset LastAttempt {get;set;} }
internal sealed class OfficialUpdates : IDisposable
{
    internal const string Repository="https://github.com/gokhangeo/rea";
    internal static readonly Uri Endpoint=new("https://api.github.com/repos/gokhangeo/rea/releases/latest");
    internal static Version InstalledVersion=>new(typeof(App).Assembly.GetName().Version!.ToString(3));
    private readonly HttpClient http;
    private readonly Store store;
    private readonly Action<string,Version> inspect;
    private readonly UpdateState state;
    private int active;
    private CancellationTokenSource? cancellation;
    internal OfficialRelease? Available {get;private set;}
    internal string? VerifiedSetup {get;private set;}
    internal string? VerifiedHash {get;private set;}
    internal string Status {get;private set;}="Güncellemeleri denetlemeye hazır.";
    internal bool Busy=>active!=0;
    internal event Action? Changed;
    internal event Action<OfficialRelease>? UpdateAvailable;
    internal OfficialUpdates(Store store,HttpMessageHandler? transport=null,Action<string,Version>? inspect=null)
    {
        this.store=store;this.inspect=inspect??InspectSetup;state=store.Load<UpdateState>("update-state");
        http=new(transport??new HttpClientHandler{AllowAutoRedirect=false});http.Timeout=TimeSpan.FromSeconds(45);
        http.DefaultRequestHeaders.UserAgent.ParseAdd("Centik/"+InstalledVersion);http.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
    }
    internal void Cancel()=>cancellation?.Cancel();
    private void Report(string text){Status=text;Changed?.Invoke();}
    internal async Task Check(bool manual,bool enabled=true)
    {
        if(!manual && (!enabled || DateTimeOffset.UtcNow-state.LastAttempt<TimeSpan.FromHours(24)))return;
        await Operation(async token=>
        {
            state.LastAttempt=DateTimeOffset.UtcNow;store.Save("update-state",state);Report("Resmi sürümler denetleniyor…");
            using var response=await Get(Endpoint,token);string json=await ReadText(response,1024*1024,token);
            Available=Parse(json,InstalledVersion);VerifiedSetup=null;VerifiedHash=null;
            Report(Available==null?"En son kararlı sürüme sahipsiniz.":"Sürüm "+Available.Version+" kullanılabilir.");
            if(Available!=null)UpdateAvailable?.Invoke(Available);
        });
    }
    internal async Task Download()
    {
        if(Available==null)return;
        await Operation(async token=>
        {
            var release=Available;VerifiedSetup=null;VerifiedHash=null;
            string folder=Path.Combine(store.DirectoryPath,"updates");Directory.CreateDirectory(folder);
            string partial=Path.Combine(folder,"Centik-Setup.exe.partial"),target=Path.Combine(folder,"Centik-Setup.exe");
            try
            {
                Report("Sağlama toplamları indiriliyor…");using var sums=await Get(release.Checksums,token);
                string hash=Checksum(await ReadText(sums,65536,token));
                using var response=await Get(release.Setup,token);
                if(response.Content.Headers.ContentLength is long length && length!=release.Size)throw new InvalidDataException("Kurulum uzunluğu sürüm meta verileriyle eşleşmiyor.");
                using(var input=await response.Content.ReadAsStreamAsync(token))
                using(var output=new FileStream(partial,FileMode.Create,FileAccess.Write,FileShare.None,81920,true))
                {
                    byte[] buffer=new byte[81920];long total=0;int read;
                    while((read=await input.ReadAsync(buffer,token))>0)
                    {total+=read;if(total>release.Size)throw new InvalidDataException("Kurulum beklenenden büyük.");await output.WriteAsync(buffer.AsMemory(0,read),token);Report($"Güncelleme indiriliyor… {total*100/release.Size}%");}
                    if(total!=release.Size)throw new InvalidDataException("Kurulum indirmesi kesintiye uğradı.");
                }
                token.ThrowIfCancellationRequested();await Task.Run(()=>{VerifyFile(partial,hash);inspect(partial,release.Version);},token);token.ThrowIfCancellationRequested();
                if(OperatingSystem.IsWindows())File.WriteAllText(partial+":Zone.Identifier","[ZoneTransfer]\r\nZoneId=3\r\nHostUrl="+release.Setup.AbsoluteUri+"\r\n");
                File.Move(partial,target,true);VerifiedHash=hash;VerifiedSetup=target;Report("İndirme doğrulandı. Hazır olduğunuzda Kur'u seçip yeniden başlatın.");
            }
            finally{if(File.Exists(partial))File.Delete(partial);}
        });
    }
    private async Task Operation(Func<CancellationToken,Task> action)
    {
        if(Interlocked.CompareExchange(ref active,1,0)!=0)return;
        using var source=new CancellationTokenSource(TimeSpan.FromMinutes(10));cancellation=source;
        try{await action(source.Token);}
        catch(OperationCanceledException){Report("Güncelleme iptal edildi veya zaman aşımına uğradı. Mevcut uygulamanız değişmedi.");}
        catch(Exception ex){store.Log(ex);Report("Güncelleme başarısız: "+ex.Message);}
        finally{cancellation=null;Interlocked.Exchange(ref active,0);Changed?.Invoke();}
    }
    private async Task<HttpResponseMessage> Get(Uri uri,CancellationToken token)
    {
        for(int attempt=0;;attempt++)
        {
            var result=await http.GetAsync(uri,HttpCompletionOption.ResponseHeadersRead,token);
            // GitHub release downloads use a signed redirect. Only its dedicated asset CDN is accepted.
            if(result.StatusCode is HttpStatusCode.Found or HttpStatusCode.TemporaryRedirect)
            {
                var next=result.Headers.Location;result.Dispose();
                if(uri.Host!="github.com" || next==null || next.Scheme!="https" || next.Host!="release-assets.githubusercontent.com" || next.UserInfo!="" || !next.IsDefaultPort)throw new InvalidDataException("Güvenilmeyen güncelleme yönlendirmesi.");
                result=await http.GetAsync(next,HttpCompletionOption.ResponseHeadersRead,token);
            }
            if((result.StatusCode==HttpStatusCode.TooManyRequests || (int)result.StatusCode>=500) && attempt<1)
            {result.Dispose();await Task.Delay(TimeSpan.FromSeconds(2),token);continue;}
            result.EnsureSuccessStatusCode();return result;
        }
    }
    private static async Task<string> ReadText(HttpResponseMessage response,int limit,CancellationToken token)
    {
        using var input=await response.Content.ReadAsStreamAsync(token);using var output=new MemoryStream();byte[] bytes=new byte[8192];int count;
        while((count=await input.ReadAsync(bytes,token))>0){if(output.Length+count>limit)throw new InvalidDataException("Güncelleme meta verisi çok büyük.");output.Write(bytes,0,count);}
        return System.Text.Encoding.UTF8.GetString(output.ToArray());
    }
    internal static OfficialRelease? Parse(string json,Version current)
    {
        using var document=JsonDocument.Parse(json);var root=document.RootElement;
        string tag=root.GetProperty("tag_name").GetString()??"";
        if(!Regex.IsMatch(tag,@"^v\d+\.\d+\.\d+$") || !Version.TryParse(tag[1..],out var version) || root.GetProperty("draft").GetBoolean() || root.GetProperty("prerelease").GetBoolean())throw new InvalidDataException("Kararlı bir resmi sürüm bekleniyordu.");
        var notes=new Uri(root.GetProperty("html_url").GetString()!);
        if(notes.AbsoluteUri!=Repository+"/releases/tag/"+tag)throw new InvalidDataException("Geçersiz sürüm kimliği.");
        Uri Asset(string name,out long size)
        {
            var assets=root.GetProperty("assets").EnumerateArray().Where(a=>a.GetProperty("name").GetString()==name).ToArray();
            if(assets.Length!=1)throw new InvalidDataException("Eksik veya yinelenen resmi varlık: "+name);
            var a=assets[0];size=a.GetProperty("size").GetInt64();
            var uri=new Uri(a.GetProperty("browser_download_url").GetString()!);
            if(uri.AbsoluteUri!=Repository+"/releases/download/"+tag+"/"+name || size<1 || size>512L*1024*1024)throw new InvalidDataException("Geçersiz resmi varlık.");
            return uri;
        }
        var setup=Asset("Centik-win-x64-Setup.exe",out long size);var sums=Asset("SHA256SUMS",out _);
        return version>current?new(version,tag,notes,setup,size,sums):null;
    }
    internal static string Checksum(string text)
    {
        var matches=text.Split('\n').Select(line=>Regex.Match(line.TrimEnd('\r'),@"^([a-fA-F0-9]{64})  Centik-win-x64-Setup\.exe$")).Where(m=>m.Success).ToArray();
        if(matches.Length!=1)throw new InvalidDataException("Geçersiz veya eksik kurulum sağlama toplamı.");return matches[0].Groups[1].Value.ToUpperInvariant();
    }
    internal static void VerifyFile(string path,string hash)
    {using var file=File.OpenRead(path);if(!string.Equals(Convert.ToHexString(SHA256.HashData(file)),hash,StringComparison.Ordinal))throw new InvalidDataException("Kurulum sağlama toplamı uyuşmuyor.");}
    internal static void InspectSetup(string path,Version expected)
    {
        using var file=File.OpenRead(path);using var reader=new BinaryReader(file);
        if(file.Length<64 || reader.ReadUInt16()!=0x5A4D)throw new InvalidDataException("Kurulum bir Windows yürütülebiliri değil.");
        file.Position=0x3C;int offset=reader.ReadInt32();if(offset<64 || offset>file.Length-6)throw new InvalidDataException("Geçersiz kurulum başlığı.");
        file.Position=offset;if(reader.ReadUInt32()!=0x4550 || reader.ReadUInt16()!=0x8664)throw new InvalidDataException("Kurulum win-x64 değil.");
        UpdateSignature.VerifyIfPresent(path);
        var version=FileVersionInfo.GetVersionInfo(path);if(new Version(version.FileMajorPart,version.FileMinorPart,version.FileBuildPart)!=expected)throw new InvalidDataException("Kurulum sürümü yayınla eşleşmiyor.");
    }
    internal static bool IsInstalled(string path)=>new[]{"Centik.exe","LumaIsland.exe","BillyNabilaIsland.exe"}.Any(name=>string.Equals(Path.GetFullPath(path),Path.Combine(Installer.InstallDirectory,name),StringComparison.OrdinalIgnoreCase));
    public void Dispose(){Cancel();http.Dispose();}
}
