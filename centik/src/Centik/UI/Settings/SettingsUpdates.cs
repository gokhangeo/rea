using System;
using System.Windows;
using System.Windows.Controls;
namespace Centik;
public sealed partial class SettingsWindow
{
    private TextBlock? updateStatus;
    private Button? downloadUpdate,installUpdate;
    private void UpdateControls()=>Dispatcher.BeginInvoke(()=>
    {
        if(downloadUpdate!=null)downloadUpdate.IsEnabled=!app.Updates.Busy && app.Updates.Available!=null;
        if(installUpdate!=null)installUpdate.IsEnabled=!app.Updates.Busy && app.Updates.VerifiedSetup!=null && OfficialUpdates.IsInstalled(Environment.ProcessPath!);
        if(updateStatus!=null)updateStatus.Text=app.Updates.Status+"\nMevcut: "+(app.Updates.Available?.Version.ToString()??"Denetlenmedi");
    });
    private void UpdatesSection()
    {
        var auto=new CheckBox{Content="Check automatically once per day",IsChecked=app.Settings.AutoCheckUpdates};UI.Named(auto,"Check automatically once per day");auto.Click+=(_,_)=>{app.Settings.AutoCheckUpdates=auto.IsChecked==true;Save();};auto.ToolTip="Son deneme en az 24 saat önceyse, normal başlangıçta bir kez denetler.";
        updateStatus=UI.Text(app.Updates.Status+"\nMevcut: "+(app.Updates.Available?.Version.ToString()??"Denetlenmedi"),12,UI.Muted);UI.Named(updateStatus,"Update status");
        page.Children.Add(Section("Updates",UI.Text("Yüklü: "+OfficialUpdates.InstalledVersion+" - win-x64",13),
            UI.Text("Denetle, mevcut sürümü ve notları incele, ardından indir. Doğrulamanın ardından Yükle ve yeniden başlat'ı seç.",12),auto,updateStatus,
            UI.Button("Check for updates",()=>{_=app.Updates.Check(true);}),
            UI.Button("Release notes",()=>ShortcutService.Open(new(){Target=app.Updates.Available?.Notes.AbsoluteUri??OfficialUpdates.Repository+"/releases"})),
            downloadUpdate=UI.Button("Download update",()=>{_=app.Updates.Download();}),
            UI.Button("Cancel update",app.Updates.Cancel),
            installUpdate=UI.Button("Install and restart",async ()=>{try{await UpdateHandoff.Prepare(app);}catch(Exception ex){updateStatus.Text=ex.Message;app.Store.Log(ex);}}),
            UI.Text(OfficialUpdates.IsInstalled(Environment.ProcessPath!)?"Yükleme; Notları kaydeder, tercihleri yedekler, Çentik'i kapatıp yeniden başlatır.":"Taşınabilir kopya: taşınabilir ZIP'i indirmek için Sürüm notları'nı kullan, çık, sonra taşınabilir dosyalarını elle değiştir. Otomatik yükleme burada kullanılamaz.",11,UI.Muted),
            UI.Text("İndirmeler resmi GitHub sürümlerini kullanır. SHA-256 bütünlüğü denetler; imzasız sağlama toplamları yayıncı kimliğini kanıtlamaz. Windows güvenlik denetimleri etkin kalır.",11,UI.Muted)));
        downloadUpdate.ToolTip="Mevcut resmi win-x64 kurulumunu indirir ve boyutunu, sürümünü ve SHA-256'sını doğrular.";
        installUpdate.ToolTip="Notları kaydeder, tercihleri yedekler, çıkar ve doğrulanmış kurulumu çalıştırır, ardından Çentik'i yeniden açar.";
        UpdateControls();
    }
}
