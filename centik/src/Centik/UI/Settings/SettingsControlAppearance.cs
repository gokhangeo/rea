using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
namespace Centik;
public sealed partial class SettingsWindow
{
    private void ControlColorSettings()
    {
        var s=app.Settings;var colors=new StackPanel();
        void Changed(Action action){action();previewMode="Panel";previewContent="Control colors";}
        ColorControl(colors,"Control background",s.ControlFill,v=>Changed(()=>s.ControlFill=v),new[]{"#16171B","#101010","#15202A","#211E1B"});
        ColorControl(colors,"Control outlines",s.ControlBorder,v=>Changed(()=>s.ControlBorder=v),new[]{"#303238","#202125","#4B5664","#645C50"});
        ColorControl(colors,"Selected controls",s.SelectionFill,v=>Changed(()=>s.SelectionFill=v),new[]{"#34373E","#22252B","#304455","#514A3D"});
        page.Children.Add(Section("Controls & outlines",UI.Text("Düğmeleri, giriş alanlarını, seçili sekmeleri ve mini pencere çerçevelerini renklendirir. Değişikliği bu denetimlerin yanında görmek için bir seçici aç. Ada ve vurgu renkleri ayrıdır.",11,UI.Muted),colors));
    }
    private FrameworkElement DetachedExample()
    {
        var s=app.Settings;var panel=new StackPanel();var layout=new StackPanel();var movement=new TranslateTransform();
        var frame=new Border{CornerRadius=new(14),BorderThickness=new(1),BorderBrush=UI.Brush(s.ControlBorder),Background=UI.Brush(s.Background),Padding=new(12),Child=layout,RenderTransform=movement};
        var header=new DockPanel{Background=Brushes.Transparent,Cursor=System.Windows.Input.Cursors.SizeAll,Margin=new(0,0,0,9)};
        var close=Icons.Button("x","Örnek pencereyi kapat",()=>frame.Opacity=frame.Opacity<1?1:.35,false,22);close.Margin=new(0);close.Background=Brushes.Transparent;DockPanel.SetDock(close,Dock.Right);header.Children.Add(close);var name=UI.Heading("Notlar",13);header.Children.Add(name);layout.Children.Add(header);
        Point? start=null;header.MouseLeftButtonDown+=(_,e)=>{if(OverlayWindow.PeekGestureBlocked(e.OriginalSource as DependencyObject))return;start=e.GetPosition(panel);header.CaptureMouse();e.Handled=true;};
        header.MouseMove+=(_,e)=>{if(start is not Point p || !header.IsMouseCaptured)return;var delta=e.GetPosition(panel)-p;movement.X=Math.Clamp(delta.X,-8,8);movement.Y=Math.Clamp(delta.Y,-4,4);};header.MouseLeftButtonUp+=(_,_)=>{start=null;header.ReleaseMouseCapture();};
        var note=UI.Input("Example detached note","Bir sonraki fikrin için küçük bir alan.");note.AcceptsReturn=true;note.TextWrapping=TextWrapping.Wrap;note.Height=70;layout.Children.Add(note);
        panel.Children.Add(frame);panel.Children.Add(UI.Text("Örnek başlığı sürükle · kapat düğmesi bu örneği soluklaştırır",10,UI.Muted));return panel;
    }
    private FrameworkElement CompanionExample()
    {
        var s=app.Settings;var panel=new StackPanel();bool notch=s.Preset=="Mac";double h=32,shoulder=notch?h*.36:0;
        var pair=new Canvas{Width=228,Height=42,HorizontalAlignment=HorizontalAlignment.Left};
        FrameworkElement Segment(double w,string text){var grid=new Grid{Width=w,Height=h};grid.Children.Add(new IslandSurface{Width=w,Height=h,SurfaceWidth=w,SurfaceHeight=h,Radius=s.Radius,Notch=notch,Fill=UI.Brush(s.Background),GlassStyle=s.GlassStyle});grid.Children.Add(new TextBlock{Text=text,FontSize=11,Foreground=Brushes.White,VerticalAlignment=VerticalAlignment.Center,HorizontalAlignment=HorizontalAlignment.Center});return grid;}
        var music=Segment(144,"Gece Sürüşü");pair.Children.Add(music);var companion=Segment(76+2*shoulder,s.SatelliteSource switch{"RAM"=>"RAM 42%","Clock"=>DateTime.Now.ToString("HH:mm"),"Calendar"=>"14:30","Countdown"=>"08:42",_=>"CPU 12%"});Canvas.SetLeft(companion,144-2*shoulder+8);pair.Children.Add(companion);
        pair.MouseEnter+=(_,_)=>Motion.Fade(companion,0,180);pair.MouseLeave+=(_,_)=>Motion.Fade(companion,1,180);panel.Children.Add(pair);panel.Children.Add(UI.Text("Bu örneğin üzerine gel: ada açılırken yardımcı geri çekilir.",11,UI.Muted));panel.Children.Add(UI.Text("Örnek değerler · gerçek okumalar adada görünür",10,UI.Muted));return panel;
    }
    private FrameworkElement ClipboardExample()
    {
        var panel=new StackPanel();panel.Children.Add(UI.Heading("Akıllı pano",16));
        var pause=new CheckBox{Content="Yakalamayı duraklat",IsChecked=false,Style=(Style)app.FindResource("SettingsSwitch")};panel.Children.Add(pause);
        var label=UI.Text("Görsel · az önce",12);var marker=new Border{Width=42,Height=42,CornerRadius=new(8),Background=UI.Brush(app.Settings.ControlFill),Child=Icons.Create("image",23)};
        var row=new DockPanel{Margin=new(0,8,0,12)};marker.Margin=new(0,0,12,0);row.Children.Add(marker);row.Children.Add(label);panel.Children.Add(row);
        var kinds=new WrapPanel();panel.Children.Add(kinds);var status=UI.Text("Örnek öğeler · panona dokunulmaz",10,UI.Muted);status.Margin=new(0,10,0,0);panel.Children.Add(status);
        foreach(string kind in new[]{"Text","Link","Image","Color"}){var button=UI.Button(kind,()=>{label.Text=kind switch{"Link"=>"example.com · temizlenmiş önizleme","Color"=>"#8E9AAF · rgb(142, 154, 175)","Text"=>"Saklamaya değer bir düşünce",_=>"Görsel · az önce"};marker.Background=UI.Brush(kind=="Color"?"#8E9AAF":app.Settings.ControlFill);marker.Child=kind=="Color"?null:Icons.Create(kind=="Link"?"link":kind=="Image"?"image":"file-text",23);status.Text=kind switch{"Link"=>"Kopyalamadan önce temizlenmiş bağlantıyı incele","Image"=>"Görseli kopyala · PNG olarak kaydet","Color"=>"HEX kopyala · RGB kopyala",_=>"Kopyala · Düz metni kopyala"};});button.FontSize=10;button.Padding=new(7,5,7,5);kinds.Children.Add(button);}
        return panel;
    }
    private FrameworkElement ControlExample(string kind)
    {
        var s=app.Settings;var panel=new StackPanel();
        if(kind=="Typography")
        {
            var title=UI.Text("Gece Sürüşü",20);title.FontWeight=s.BoldMediaTitle?FontWeights.SemiBold:FontWeights.Normal;panel.Children.Add(title);panel.Children.Add(UI.Text("Sanatçı · medya başlığı",11,UI.Muted));
            var heading=UI.Text("Dosya rafı",17);heading.FontWeight=s.BoldHeadings?FontWeights.SemiBold:FontWeights.Normal;heading.Margin=new(0,20,0,5);panel.Children.Add(heading);panel.Children.Add(UI.Text("Hazırlanan dosyalar özgün konumlarında kalır.",12,UI.Muted));return panel;
        }
        if(kind=="Panel navigation")
        {
            var tabs=new Grid();for(int i=0;i<4;i++)tabs.ColumnDefinitions.Add(new());var state=UI.Text("Media",18);state.Margin=new(0,18,0,8);int active=0;string[] names={"Media","Status","Launch","Tools"};Button[] buttons=new Button[4];
            void Select(int index){active=(index+4)%4;state.Text=names[active];for(int i=0;i<4;i++){buttons[i].Background=i==active?UI.Brush(s.SelectionFill):Brushes.Transparent;buttons[i].Foreground=i==active?UI.ContrastText(s.SelectionFill):Brushes.White;}}
            for(int i=0;i<4;i++){int index=i;var button=UI.Button(names[i],()=>Select(index));button.Padding=new(3,6,3,6);button.FontSize=10;button.Margin=new(0,0,3,0);buttons[i]=button;Grid.SetColumn(button,i);tabs.Children.Add(button);}
            panel.Children.Add(tabs);panel.Children.Add(state);var arrows=UI.Row(UI.Button("Önceki",()=>Select(active-1)),UI.Button("Sonraki",()=>Select(active+1)));panel.Children.Add(arrows);panel.Children.Add(UI.Text("Sekmeleri dene · gerçek panelde Shift+tekerlek",11,UI.Muted));Select(0);return panel;
        }
        panel.Children.Add(UI.Text("Düğmeler ve giriş alanları",16));
        var buttonSample=UI.Button("Örnek düğme",()=>{});buttonSample.Background=UI.Brush(s.ControlFill);buttonSample.Foreground=UI.ContrastText(s.ControlFill);buttonSample.BorderBrush=UI.Brush(s.ControlBorder);buttonSample.BorderThickness=new(1);panel.Children.Add(buttonSample);
        var entry=UI.Input("Color preview input","Örnek giriş");entry.Background=UI.Brush(s.ControlFill);entry.Foreground=UI.ContrastText(s.ControlFill);entry.BorderBrush=UI.Brush(s.ControlBorder);panel.Children.Add(entry);
        var selected=UI.Button("Seçili sekme",()=>{});selected.Background=UI.Brush(s.SelectionFill);selected.Foreground=UI.ContrastText(s.SelectionFill);panel.Children.Add(selected);
        var toggle=new CheckBox{Content="Yakalamayı duraklat",IsChecked=true,Style=(Style)app.FindResource("SettingsSwitch")};panel.Children.Add(toggle);
        var selection=new CheckBox{Content="Seçili dosya",IsChecked=true,Style=(Style)app.FindResource("TaskToggle")};panel.Children.Add(selection);
        panel.Children.Add(UI.Text("Örnek denetimler · değişiklikler uygulamaya yansır",11,UI.Muted));return panel;
    }
}
