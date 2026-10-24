using System;
using System.Windows.Controls;
namespace Centik;
public sealed partial class SettingsWindow
{
    private void NotificationSettings()
    {
        var s=app.Settings;CompletionSoundSettings();
        var delivery=new ComboBox{ItemsSource=new[]{"Notch","Windows","Both","Off"},SelectedItem=s.NotificationDelivery};UI.Named(delivery,"Notification delivery");delivery.SelectionChanged+=(_,_)=>{s.NotificationDelivery=delivery.SelectedItem?.ToString()??"Notch";Save();};
        page.Children.Add(Section("Notifications",
            delivery,
            Check("Timer finished",s.NotifyTimer,v=>s.NotifyTimer=v),Check("Focus finished",s.NotifyFocus,v=>s.NotifyFocus=v),Check("Downloads ready",s.NotifyDownloads,v=>s.NotifyDownloads=v),Check("Update available",s.NotifyUpdates,v=>s.NotifyUpdates=v),Check("Important app failures",s.NotifyFailures,v=>s.NotifyFailures=v),
            UI.Text("Çentik, kompakt bir gösterge gösterir. Ayrıntılar için üzerine gel veya tıkla. Elle gizlenen adalar yerine Windows'u kullanır. Ses düzeyi ve parlaklık önceliklidir; paneller ve düzenleyiciler bozulmadan kalır. Bekleyen bildirimler zaman aşımına uğrar; geçmiş kaydedilmez.",11,UI.Muted),
            UI.Button("Bekleyen bildirimleri gör",app.OpenNotifications),UI.Text("Klavye: adayı aç, Ctrl+K'ye bas, Bildirimler'i seç, sonra Enter. Windows teslimi, Windows bildirim tercihlerini izler.",11,UI.Muted)));
        NotificationPresentationSettings();
        var folder=UI.Text(s.DownloadsFolder.Length==0?"Windows İndirilenler bilinen klasörü (taşınan klasörler dâhil)":s.DownloadsFolder,11,UI.Muted);
        page.Children.Add(Section("Downloads",Check("Infer completed arrivals in a download folder",s.DownloadsEnabled,v=>s.DownloadsEnabled=v),folder,
            UI.Button("Klasör seç…",()=>{var dialog=new Microsoft.Win32.OpenFolderDialog{Title="Klasör gelişlerini izle"};if(dialog.ShowDialog(this)==true){s.DownloadsFolder=dialog.FolderName;folder.Text=dialog.FolderName;Save();}}),
            UI.Button("Windows İndirilenler'i kullan",()=>{s.DownloadsFolder="";folder.Text="Windows İndirilenler bilinen klasörü";Save();}),
            UI.Text("Varsayılan olarak kapalı. Yalnızca bu klasörü izler, alt klasörleri asla. Var olan dosyalar yok sayılır. İki saniye boyunca kararlı boyutta, okunabilir son bir ad bir gelişe işaret eder; bu, bir tarayıcının indirmeyi bitirdiğini her zaman kanıtlayamaz. Orijinaller yerinde kalır. Bir grup dosyayı birlikte sürükle, Klasörde göster ya da Rafa ekle (40 öğe sınırı).",11,UI.Muted),
            UI.Text("Devre dışı bırakıldığında izleme durur. Bir klasörün bağlantısı kesilir veya erişilemez olursa, izlemeyi kapatıp açarak yeniden başlat. Klasör yolları yerleşim dışa aktarımlarına dâhil edilmez.",11,UI.Muted)));
    }
}
