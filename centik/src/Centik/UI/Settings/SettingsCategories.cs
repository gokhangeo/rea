using System;
using System.Collections.Generic;
using System.Linq;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
namespace Centik;

internal sealed record SettingsCategory(string Id,string Title,string Group,string Description,string Terms,string[]? Sections=null);
public sealed partial class SettingsWindow
{
    private static readonly SettingsCategory[] SettingsCategories=
    {
        new("Appearance","Görünüm ve renkler","Kişiselleştir","Dock'un şeklini, renklerini ve yazı stilini seçin.","appearance theme warna tampilan font shape glass",new[]{"Shape","Settings material","Color & material","Surface","Controls & outlines","Typography","Text and navigation"}),
        new("Ambience","Atmosfer","Kişiselleştir","Işığın nerede belireceğini ve müziğinizi nasıl takip edeceğini seçin.","ambient ambience cahaya warna glow spread softness light reach closed dock tertutup",new[]{"Ambient light"}),
        new("Motion","Animasyon ve hız","Kişiselleştir","Animasyonu ayarlayın; akıcılık ile performans arasında denge kurun.","animation animasi motion fps speed performance ram cpu hemat reduce",new[]{"Motion","Animation performance"}),
        new("Layout","Konum ve boyut","Dock","Dock'u ekran kenarına yerleştirin ya da serbestçe taşıyın.","position posisi pindah atas bawah kanan kiri monitor size resize ukuran"),
        new("Visibility","Göster ve gizle","Dock","Dock'un ne zaman gizleneceğini, saklanacağını veya önizleme göstereceğini seçin.","hide show tuck sembunyi tutup youtube fullscreen maximized hover preview",new[]{"Automatic tucking","Manual tuck shortcut","Hide or show the island","Automatic activity"}),
        new("Controls","Fare ve klavye","Dock","Fare hareketlerini, üzerine gelme davranışını ve klavye kısayollarını ayarlayın.","mouse keyboard shortcut hotkey swipe scroll gesture klik tombol"),
        new("Content","Araçlar ve bileşenler","Özellikler","Bileşenleri açıp kapatın, sıralayın ve odak oturumlarını yapılandırın.","widget tools urutan notes task timer focus stopwatch pomodoro"),
        new("Audio","Müzik ve ses","Özellikler","Müzik denetimlerini, şarkı sözlerini ve ses görselleştiriciyi özelleştirin.","music spotify volume musik suara waveform spectrum lirik lyrics audio microphone"),
        new("Clipboard","Pano","Özellikler","Kopyalanan metin ve görsellerin bu cihazda kaydedilip kaydedilmeyeceğini denetleyin.","clipboard salin copy paste capture history riwayat gambar",new[]{"Clipboard history"}),
        new("Weather","Hava durumu","Özellikler","Şehrinizi seçin; hava durumunu Celsius veya Fahrenheit olarak görün.","weather cuaca kota city temperature suhu rain hujan"),
        new("System","Saat ve sistem","Özellikler","Saat biçimini ve bileşenlerde gösterilen bilgisayar bilgilerini seçin.","clock jam tanggal date battery cpu ram network storage system"),
        new("Launch","Uygulamalar ve kısayollar","Özellikler","Favori uygulamalarınızı seçin ve tek tıkla rutinler oluşturun.","apps launch aplikasi shortcuts explorer routine activities"),
        new("AI","Çentik AI","Özellikler","Bir yapay zekâ sağlayıcısı bağlayın ve yardımcı tercihlerini yönetin.","ai naby hermes gemini live voice provider api key chat model token"),
        new("Notifications","Bildirimler","Uygulama","Hangi uyarıların görüneceğini, seslerini ve sessiz saatleri seçin.","notifications notif pemberitahuan alert sound bunyi quiet hours downloads",new[]{"Notifications","Notification presentation","Downloads","Completion sound"}),
        new("Application","Genel","Uygulama","Başlangıç, güncellemeler, yerel veriler ve uygulama tercihlerini yönetin.","general startup update version install storage data reset backup mulai windows")
    };
    private static SettingsCategory Category(string id)=>SettingsCategories.FirstOrDefault(c=>c.Id==id)??SettingsCategories[0];
    private static string CategoryTitle(string id)=>Category(id).Title;
    private static string CategoryId(string title)=>SettingsCategories.FirstOrDefault(c=>c.Title==title)?.Id??title;
    private static string SettingsRoot(string id)=>id switch{"Ambience" or "Motion"=>"Appearance","Visibility"=>"Controls","Clipboard"=>"Content","Notifications"=>"Application",_=>id};
    private static string FriendlySection(string title)=>title switch
    {
        "Shape"=>"Dock şekli","Color & material"=>"Renkler","Settings material"=>"Ayarlar penceresi","Surface"=>"Cam görünümü","Controls & outlines"=>"Düğmeler ve giriş alanları","Typography"=>"Yazı kalınlığı","Text and navigation"=>"Sekme etiketleri","Animation performance"=>"Akıcılık ve performans","Display"=>"Ekran","Closed island"=>"Kompakt dock","Open panel"=>"Genişletilmiş dock","Enabled widgets & order"=>"Bileşenleriniz","Notch gestures"=>"Fare işlemleri","Global hotkey"=>"Dock açma kısayolu","Automatic activity"=>"Otomatik önizlemeler","Clipboard history"=>"Pano yakalama","Completion sound"=>"Zamanlayıcı ve odak sesi","About Centik"=>"Çentik hakkında","Activity actions"=>"Etkinlik eylemleri","Ambient light"=>"Ortam ışığı","Audio behavior"=>"Ses davranışı","Automatic tucking"=>"Otomatik gizleme","Behavior"=>"Davranış","Clock"=>"Saat","Closed cover gestures"=>"Kapalı kapak hareketleri","Closed media display"=>"Kapalı medya gösterimi","Detachable previews"=>"Ayrılabilir önizlemeler","Dock placement"=>"Dock yerleşimi","Downloads"=>"İndirilenler","Drag to Island"=>"Adaya sürükle","Focus sessions"=>"Odak oturumları","Hide or show the island"=>"Adayı gizle veya göster","Hover & swipe"=>"Üzerine gelme ve kaydırma","Hover pages"=>"Üzerine gelme sayfaları","Hover preview"=>"Üzerine gelme önizlemesi","Launch display"=>"Başlatma gösterimi","Layout profile"=>"Yerleşim profili","Live feedback"=>"Canlı geri bildirim","Live signal"=>"Canlı sinyal","Local storage"=>"Yerel depolama","Lyrics"=>"Şarkı sözleri","Manual tuck shortcut"=>"Elle gizleme kısayolu","Monitor details"=>"İzleme ayrıntıları","Motion"=>"Hareket","Move & resize"=>"Taşı ve yeniden boyutlandır","Notification presentation"=>"Bildirim sunumu","Notifications"=>"Bildirimler","Panel navigation"=>"Panel gezinmesi","Reset preferences"=>"Tercihleri sıfırla","Shortcuts"=>"Kısayollar","Stopwatch"=>"Kronometre","Two live activities"=>"İki canlı etkinlik","Updates"=>"Güncellemeler","Visualizer"=>"Görselleştirici","Volume mixer"=>"Ses karıştırıcı","Individual tools"=>"Tek tek araçlar","Waveform source"=>"Dalga biçimi kaynağı",_=>title
    };
    private void FilterSettingsCategory(string id)
    {
        var definition=Category(id);
        var excluded=id switch{"Content"=>new[]{"Clipboard history"},"Application"=>Category("Notifications").Sections!,"Controls"=>Category("Visibility").Sections!,_=>Array.Empty<string>()};
        foreach(var section in page.Children.OfType<Border>().Where(b=>b.Tag is string).ToArray())
        {
            string key=(string)section.Tag;
            if(definition.Sections is {} allowed && !allowed.Contains(key) || excluded.Contains(key))page.Children.Remove(section);
        }
        if(definition.Sections!=null)foreach(var note in page.Children.OfType<TextBlock>().Skip(1).ToArray())page.Children.Remove(note);
        var retained=page.Children.OfType<Border>().Select(b=>b.Tag as string).ToHashSet();settingExamples.RemoveAll(scene=>!retained.Contains(scene.Section));
    }
    private void WeatherSettingsPage()
    {
        page.Children.Add(new Border{Child=new WeatherView(app,true),Background=UI.Brush("#16191F"),BorderBrush=UI.Brush("#2A2F39"),BorderThickness=new(.7),CornerRadius=new(14),Padding=new(16),Margin=new(0,0,0,16)});
    }
}
