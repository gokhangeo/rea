using System;
using System.Windows;
using System.Windows.Input;

namespace Centik;

public sealed partial class SettingsWindow
{
    private void FlushPreferenceChanges()
    {
        Keyboard.ClearFocus();saveTimer.Stop();app.FlushPreferenceEditors();app.ChangeSettings();
    }
    private void ResetPreferences(string? category)
    {
        if(MessageBox.Show(this,PreferenceReset.Summary(category),"Tercihleri sıfırla",MessageBoxButton.OKCancel,MessageBoxImage.Question)!=MessageBoxResult.OK)return;
        try{FlushPreferenceChanges();PreferenceReset.Reset(app.Store,app.Settings,category);app.ChangeSettings();Navigate(current);status.Text="Tercihler sıfırlandı. Yedek, Uygulama bölümünden geri yüklenebilir.";}
        catch(Exception ex){status.Text="Sıfırlama iptal edildi: "+ex.Message;app.Store.Log(ex);}
    }
    private void RestorePreferences()
    {
        if(MessageBox.Show(this,"Son tercih yedeği geri yüklensin mi? Başlangıç, pano yakalama/geçmişi ve kayıtlı içerik olduğu gibi kalır.","Tercihleri geri yükle",MessageBoxButton.OKCancel,MessageBoxImage.Question)!=MessageBoxResult.OK)return;
        try{FlushPreferenceChanges();PreferenceReset.Restore(app.Store,app.Settings);app.ChangeSettings();Navigate(current);status.Text="Tercih yedeği geri yüklendi.";}
        catch(Exception ex){status.Text="Geri yükleme iptal edildi: "+ex.Message;app.Store.Log(ex);}
    }
}
