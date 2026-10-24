using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace Centik;

// Compatibility host. The island and this window share the same runtime and UI.
internal sealed class AssistantWindow : Window
{
    internal AssistantWindow(App app)
    {
        Title="Centik · Yardımcı";
        AssistantWindowChrome.Place(this,app.Settings,480,690);
        var view=new AssistantView(app);
        var outer=new ScrollViewer{Content=view,VerticalScrollBarVisibility=ScrollBarVisibility.Auto,HorizontalScrollBarVisibility=ScrollBarVisibility.Disabled};
        Content=AssistantWindowChrome.Frame(this,"Centik · AI yardımcısı",outer);
        AddHandler(Keyboard.KeyDownEvent,new KeyEventHandler((_,e)=>
        {if(e.Key==Key.Escape){app.Assistant.StopSession();e.Handled=true;}}),true);
        StateChanged+=(_,_)=>{if(WindowState==WindowState.Minimized)app.Assistant.StopSession();};
        IsVisibleChanged+=(_,_)=>{if(!IsVisible)app.Assistant.StopSession();};
        Closed+=(_,_)=>{view.Dispose();app.Assistant.StopSession();};
    }

    // Provider settings retain their existing button styling API.
    internal static Button Pill(string text,Action action,bool primary=false)=>AssistantView.Pill(text,action,primary);
}
