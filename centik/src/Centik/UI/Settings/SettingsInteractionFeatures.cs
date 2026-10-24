using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace Centik;

public sealed partial class SettingsWindow
{
    private void InteractionFeatureControls()
    {
        var s=app.Settings;
        var edge=new StackPanel();AddSlider(edge,"Tucked edge height",s.TuckEdge,3,12,v=>s.TuckEdge=v," DIP");
        page.Children.Add(Section("Automatic tucking",Check("Always tucked",s.AlwaysTucked,v=>s.AlwaysTucked=v),edge,Check("Tuck above maximized apps",s.HideMaximized,v=>s.HideMaximized=v),Check("Tuck above fullscreen apps",s.HideFullscreen,v=>s.HideFullscreen=v),UI.Text("Chrome veya başka bir uygulama bu ekranı doldurduğunda üstte küçük bir kenar bırakır. Önizlemeyi ortaya çıkarmak için işaretçiyi o kenara götürün. Elle gizleme yine de tüm adayı gizler.",11,UI.Muted)));
        page.Children.Add(Section("Panel navigation",Check("Swipe between open panels",s.PanelSwipe,v=>s.PanelSwipe=v),UI.Text("Panel değiştirmek için boş alanda kaydırın ya da Shift+tekerlek kullanın. Uzun listelerde dikey kaydırma korunur. Klavye: Ctrl+Tab.",11,UI.Muted)));
        page.Children.Add(Section("Drag to Island",Check("Reveal shelf when dragging files",s.DropReveal,v=>s.DropReveal=v),UI.Text("Dosyaları adanın üzerine ya da gizlenmiş kenarına sürükleyin. Referansları sahnelemek için bırakın; birden çok raf öğesini seçip birlikte sürükleyin. Elle gizleme gizli kalır.",11,UI.Muted)));
        page.Children.Add(Section("Detachable previews",Check("Allow detachable previews",s.DetachablePreviews,v=>s.DetachablePreviews=v),UI.Text("Mini pencere yerleştirmek için tutamacı sürükleyin ya da Ayır'ı seçin. Müzik, Zamanlayıcı, Odak, Takvim, Notlar, Raf ve Sistem desteklenir. Kapatmak müzik ve oturumları çalışır tutar.",11,UI.Muted)));
        page.Children.Add(Section("Closed cover gestures",Check("Control playback from album cover",s.CoverGestures,v=>s.CoverGestures=v),UI.Text("Ses bölümünde Albüm kapağını seçin. Oynat/duraklat için tıklayın; oturum seçmek için 500 ms basılı tutun. Klavye: oynatma için Ctrl+Space, oturumlar için Ctrl+M.",11,UI.Muted)));
        var source=new ComboBox();var sourceValues=new[]{"Auto","Countdown","CPU","RAM","Calendar","Clock"};
        foreach(var label in new[]{"Automatic","Timer / Focus","CPU usage","Memory usage","Next calendar event","Clock"})source.Items.Add(label);
        source.SelectedIndex=System.Array.IndexOf(sourceValues,s.SatelliteSource);UI.Named(source,"Companion beside music");
        source.SelectionChanged+=(_,_)=>{if(source.SelectedIndex>=0){s.SatelliteSource=sourceValues[source.SelectedIndex];previewMode="Hover";previewContent="Two activities";Save();}};
        page.Children.Add(Section("Two live activities",Check("Show companion beside music",s.ActivitySatellite,v=>s.ActivitySatellite=v),UI.Text("Yardımcı",12),source,UI.Text("Otomatik; bir geri sayım, yaklaşan bir etkinlik, sonra CPU seçer. Ya da sabit bir kaynak seçin. Önizlemek için üzerine gelin; açmak için tıklayın. Ada açılırken, geri bildirim sırasında veya alan kısıtlıyken gizlenir.",11,UI.Muted)));

    }
    private FrameworkElement FeaturePreview(string kind)
    {
        var panel=new StackPanel();
        var s=app.Settings;
        if(kind=="Activities" && !s.ActivitiesEnabled)return UI.Text("Etkinlikler kapalı. Kayıtlı rutinler korunur.",12,UI.Muted);if(kind=="Smart clipboard")return ClipboardExample();
        if(kind is "Control colors" or "Typography" or "Panel navigation")return ControlExample(kind);
        string? inactive=kind switch
        {
            "Drop zone" when !s.DropReveal=>"Drop reveal is off. Open the shelf to add files.",
            "Detached preview" when !s.DetachablePreviews=>"Detachable previews are off.",
            "Cover gestures" when !s.CoverGestures=>"Cover gestures are off. Click to open the panel.",
            "Cover gestures" when s.ClosedMediaIcon!="Artwork"=>"Choose Artwork in Appearance to use cover gestures.",
            "Two activities" when !s.ActivitySatellite=>"Companion is off. Music stays in the island.",
            _=>null
        };
        if(inactive!=null){panel.Children.Add(UI.Text(inactive,12,UI.Muted));return panel;}
        if(kind=="Drop zone")
        {
            var row=new StackPanel{Orientation=Orientation.Horizontal,HorizontalAlignment=HorizontalAlignment.Center};
            for(int i=0;i<3;i++){var icon=Icons.Create(i==1?"music":"nav-tools",26);icon.Margin=new(9,0,9,0);var move=new TranslateTransform();icon.RenderTransform=move;row.Children.Add(icon);int index=i;previewRealtimeFrames.Add(t=>move.Y=app.Settings.ReduceMotion?0:Math.Sin(t*2+index)*3);}
            panel.Children.Add(row);panel.Children.Add(UI.Text("3 dosya · sahnelemek için bırak",12));
        }
        else if(kind=="Detached preview")return DetachedExample();
        else if(kind=="Cover gestures")
        {
            var state=UI.Text("Oynatmak için kapağa tıkla",12);var cover=UI.Button("Albüm kapağı",()=>state.Text=state.Text.Contains("playing")?"Playback paused":"Music is playing");cover.Content=Icons.Create("music",24);cover.Width=44;cover.Height=44;cover.HorizontalAlignment=HorizontalAlignment.Left;panel.Children.Add(cover);panel.Children.Add(state);
        }
        else if(kind=="Two activities")return CompanionExample();
        else if(kind=="Activities")
        {panel.Children.Add(UI.Text("Çalışma",16));panel.Children.Add(UI.Text("Çalışma alanını aç → 45 dk Odak → Uyanık tut",11,UI.Muted));var result=UI.Text("Hazır",11,UI.Muted);var start=UI.Button("Önizlemeyi dene",()=>result.Text="Çalışma alanı hazır · Odak başladı");panel.Children.Add(start);panel.Children.Add(result);}
        else
        {panel.Children.Add(new Border{Height=28,Background=UI.Brush("#8E9AAF"),CornerRadius=new(4)});panel.Children.Add(UI.Text("#8E9AAF · rgb(142, 154, 175)",12));panel.Children.Add(UI.Text("HEX kopyala    RGB kopyala",11,UI.Muted));}
        return panel;
    }
}
