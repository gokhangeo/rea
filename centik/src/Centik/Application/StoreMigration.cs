using System;
using System.IO;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Centik;

public sealed partial class Store
{
    private static string ResolveMigration(string legacy,string destination,out string? warning)
    {
        legacy=Path.GetFullPath(legacy);destination=Path.GetFullPath(destination);warning=null;
        if(Directory.Exists(destination))
        {
            if(Directory.Exists(legacy))warning="Hem Centik hem LumaIsland verileri mevcut. Centik verileri kullanımda; kaldırmadan önce korunan LumaIsland yedeğini gözden geçirin.";
            return destination;
        }
        if(!Directory.Exists(legacy))return destination;
        string staging=destination+".migration-"+Guid.NewGuid().ToString("N");
        try
        {
            CopyDirectory(legacy,staging);
            foreach(string file in Directory.EnumerateFiles(staging,"*.json",SearchOption.AllDirectories))
            {using var json=JsonDocument.Parse(File.ReadAllText(file));if(json.RootElement.ValueKind is not (JsonValueKind.Object or JsonValueKind.Array))throw new InvalidDataException("Geçersiz kayıtlı belge: "+Path.GetFileName(file));}
            string settings=Path.Combine(staging,"settings.json");
            if(File.Exists(settings))
            {var preferences=JsonSerializer.Deserialize<Settings>(File.ReadAllText(settings))??throw new InvalidDataException("Ayarlar boş.");preferences.Normalize();}
            ValidateDocument<ProductivityData>(staging,"productivity");ValidateDocument<SessionState>(staging,"session");ValidateDocument<ActivityActionsData>(staging,"activities");ValidateDocument<System.Collections.Generic.List<CalendarEvent>>(staging,"calendar");
            string productivity=Path.Combine(staging,"productivity.json");
            if(File.Exists(productivity))
            {
                var data=JsonNode.Parse(File.ReadAllText(productivity))!;
                if(data["Clips"] is JsonArray clips)foreach(var clip in clips)
                {
                    if(clip?["Kind"]?.GetValue<string>()!="image")continue;
                    string path=clip["ImagePath"]?.GetValue<string>()??"";
                    string cache=Path.GetFullPath(Path.Combine(legacy,"clipboard-images"))+Path.DirectorySeparatorChar;
                    if(!Path.IsPathFullyQualified(path) || !Path.GetFullPath(path).StartsWith(cache,StringComparison.OrdinalIgnoreCase))continue;
                    string relative=Path.GetRelativePath(legacy,path);
                    if(!File.Exists(Path.Combine(staging,relative)))throw new InvalidDataException("Kaydedilmiş bir pano görüntüsü eksik.");
                    clip["ImagePath"]=Path.Combine(destination,relative);
                }
                File.WriteAllText(productivity,data.ToJsonString(Options));
            }
            // A same-volume directory rename publishes only the fully copied and validated tree.
            Directory.Move(staging,destination);
            warning="Yerel veriler Centik'e taşındı. Özgün LumaIsland klasörü yedek olarak korundu.";
            return destination;
        }
        catch(Exception ex) when(ex is IOException or UnauthorizedAccessException or JsonException or InvalidOperationException or ArgumentException or NotSupportedException)
        {
            warning="Veri taşıma tamamlanamadı; özgün LumaIsland verileri kullanımda kalıyor. Depolama izinlerini gözden geçirip yeniden deneyin. "+ex.Message;
            return legacy;
        }
    }
    private static void ValidateDocument<T>(string directory,string name)
    {
        string file=Path.Combine(directory,name+".json");
        if(File.Exists(file) && JsonSerializer.Deserialize<T>(File.ReadAllText(file))==null)throw new InvalidDataException(name+" verileri boş.");
    }
    private static void CopyDirectory(string source,string destination)
    {
        if((File.GetAttributes(source)&FileAttributes.ReparsePoint)!=0)throw new IOException("Taşıma sırasında veri klasörleri bağlantı olamaz.");
        Directory.CreateDirectory(destination);
        foreach(string file in Directory.EnumerateFiles(source))
        {
            if((File.GetAttributes(file)&FileAttributes.ReparsePoint)!=0)throw new IOException("Taşıma sırasında veri dosyaları bağlantı olamaz.");
            if((File.GetAttributes(file)&FileAttributes.ReadOnly)!=0)throw new UnauthorizedAccessException("Eski bir veri dosyası salt okunur: "+Path.GetFileName(file));
            File.Copy(file,Path.Combine(destination,Path.GetFileName(file)));
        }
        foreach(string directory in Directory.EnumerateDirectories(source))CopyDirectory(directory,Path.Combine(destination,Path.GetFileName(directory)));
    }
}
