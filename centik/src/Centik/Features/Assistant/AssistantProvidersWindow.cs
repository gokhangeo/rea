using System.Windows;
using System.Windows.Controls;

namespace Centik;

// Compatibility fixture host. Application navigation uses Settings > AI.
internal sealed class AssistantProvidersWindow : Window
{
    internal AssistantProvidersWindow(App app)
    {
        Title="Centik · AI providers";AssistantWindowChrome.Place(this,app.Settings,600,740);
        var editor=new AssistantProviderSettings(app,false);
        Content=AssistantWindowChrome.Frame(this,"Centik · AI providers",new ScrollViewer{Content=editor,VerticalScrollBarVisibility=ScrollBarVisibility.Auto,HorizontalScrollBarVisibility=ScrollBarVisibility.Disabled});
        Closed+=(_,_)=>editor.ClearSecrets();
    }
}
