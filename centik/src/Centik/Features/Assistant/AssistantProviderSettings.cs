using System;
using System.Linq;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;

namespace Centik;

internal sealed class AssistantProviderSettings : UserControl
{
    private readonly AssistantRuntime ai;
    private readonly ComboBox list=new(),preset=new(){ItemsSource=AssistantProviderCatalog.Presets,SelectedIndex=0},voiceProvider=new(),chatProvider=new();
    private readonly ComboBox protocol=new(){ItemsSource=new[]{"ChatCompletions","Responses","Anthropic","Gemini"}};
    private readonly ComboBox realtimeProtocol=new(){ItemsSource=AssistantVoiceSession.Protocols};
    private readonly TextBox name=UI.Input("Provider name",""),url=UI.Input("API base URL",""),model=UI.Input("Chat model",""),realtimeUrl=UI.Input("Realtime WebSocket URL",""),realtimeModel=UI.Input("Realtime model",""),voiceName=UI.Input("Voice name",""),instructions=UI.Input("Assistant instructions","");
    private readonly PasswordBox key=new(){MaxLength=2000};
    private readonly TextBlock status=UI.Text("",11,UI.Muted),keyStatus=UI.Text("",11,UI.Muted);
    private readonly TextBlock hermesHelp=UI.Text("Hermes Agent kendi ağ geçidinde çalışır (varsayılan localhost:8642). API_SERVER_KEY değerini girin ve hermes-agent modelini kullanın. Araçları, Çentik AI onaylarından bağımsız olarak kendi izinleriyle çalışır. Temizle, sunucu belleğini silmeden yeni bir sohbet başlatır. Canlı ses ayrı bir sağlayıcı kullanır.",11,UI.Muted);
    private readonly Button enable,vision,tools;
    private bool syncing,imageInput,toolInput;
    private readonly Expander connectionEditor=new(){Header="Sağlayıcı bağlantısı",IsExpanded=true,Margin=new(0,14,0,8)};
    internal AssistantProviderSettings(App app,bool embedded=true)
    {
        ai=app.Assistant;Resources.MergedDictionaries.Add(new ResourceDictionary{Source=new Uri("/Centik;component/Features/Assistant/AssistantTheme.xaml",UriKind.Relative)});
        var page=new StackPanel{Margin=embedded?new Thickness(0):new Thickness(24,20,24,24)};
        var header=new DockPanel();var orb=new AssistantOrb{Width=54,Height=54,ReduceMotion=app.Settings.ReduceMotion};header.Children.Add(orb);var intro=new StackPanel{Margin=new(12,0,0,0)};intro.Children.Add(UI.Heading("AI'nız, sağlayıcınız",22));intro.Children.Add(UI.Text("Bir API bağlayın. Neyi görüp yapabileceğini seçin.",12,UI.Muted));header.Children.Add(intro);if(!embedded)page.Children.Add(header);
        enable=AssistantWindow.Pill("",()=>{ai.Options.Enabled=!ai.Options.Enabled;SaveConfiguration();RefreshEnabled();});enable.Margin=new(0,16,0,12);page.Children.Add(enable);
        var character=AssistantWindow.Pill(ai.Options.IslandCharacter?"Boşta karakter görünür":"Boşta karakter gizli",()=>{});
        character.Click+=(_,_)=>{try{ai.SetIslandCharacter(!ai.Options.IslandCharacter);character.Content=ai.Options.IslandCharacter?"Boşta karakter görünür":"Boşta karakter gizli";}catch{status.Text="Boşta karakter tercihi kaydedilemedi.";}};
        page.Children.Add(character);
        Label(page,"Sohbet için kullanılan sağlayıcı",chatProvider);UI.Named(chatProvider,"Chat provider");chatProvider.SelectionChanged+=(_,_)=>{if(!syncing && chatProvider.SelectedItem is AssistantProvider selected && selected.Id!=ai.Options.SelectedProvider)ai.SelectProvider(selected.Id);};
        Label(page,"Ses için kullanılan sağlayıcı",voiceProvider);voiceProvider.SelectionChanged+=(_,_)=>{if(!syncing && voiceProvider.SelectedItem is AssistantProvider selected){ai.Options.SelectedVoiceProvider=selected.Id;SaveConfiguration();}};
        var add=new Grid();add.ColumnDefinitions.Add(new());add.ColumnDefinitions.Add(new(){Width=GridLength.Auto});add.Children.Add(preset);var addButton=AssistantWindow.Pill("Sağlayıcı ekle",AddProvider);addButton.Margin=new(8,0,0,0);Grid.SetColumn(addButton,1);add.Children.Add(addButton);page.Children.Add(add);
Label(page,"Kayıtlı bir sağlayıcıyı düzenle",list);UI.Named(list,"Saved AI providers");list.SelectionChanged+=(_,_)=>{if(!syncing){LoadSelected();connectionEditor.IsExpanded=true;}};
        var connection=new StackPanel();
        Label(connection,"Ad",name);name.MaxLength=60;Label(connection,"Sohbet API protokolü",protocol);Label(connection,"API temel URL'si",url);url.MaxLength=2048;Label(connection,"Sohbet modeli kimliği",model);model.MaxLength=160;
        Label(connection,"API anahtarı · Windows Kimlik Bilgileri Yöneticisi",key);UI.Named(key,"API key");connection.Children.Add(keyStatus);
        hermesHelp.Margin=new(0,10,0,10);connection.Children.Add(hermesHelp);
        connection.Children.Add(UI.Text("Anahtar, ayarlara veya dışa aktarılan düzenlere asla yazılmaz. Bir sunucuyu değiştirmek, yeni bir anahtar girmediğiniz sürece kayıtlı anahtarını temizler. Yerel sağlayıcılar burayı boş bırakabilir.",11,UI.Muted));
        vision=AssistantWindow.Pill("",()=>{imageInput=!imageInput;RefreshCapabilities();});tools=AssistantWindow.Pill("",()=>{toolInput=!toolInput;RefreshCapabilities();});var flags=new WrapPanel{Margin=new(0,10,0,8)};flags.Children.Add(vision);flags.Children.Add(tools);connection.Children.Add(flags);connection.Children.Add(UI.Text("Bunları yalnızca seçtiğiniz model görüntü girişini ve işlev araçlarını destekliyorsa etkinleştirin. Uyumlu bir sohbet API'si gerçek zamanlı ses desteği anlamına gelmez.",11,UI.Muted));
        var realtime=new StackPanel();Label(realtime,"Ses protokolü",realtimeProtocol);Label(realtime,"Gerçek zamanlı WebSocket URL'si",realtimeUrl);realtimeUrl.MaxLength=2048;realtime.Children.Add(AssistantWindow.Pill("Protokol URL'sini kullan",()=>realtimeUrl.Text=AssistantVoiceSession.DefaultUrl(realtimeProtocol.SelectedItem?.ToString()??"OpenAIRealtime")));Label(realtime,"Gerçek zamanlı model kimliği",realtimeModel);realtimeModel.MaxLength=160;Label(realtime,"Sağlayıcı ses adı",voiceName);voiceName.MaxLength=60;realtime.Children.Add(UI.Text("OpenAI Realtime ve Gemini Live farklı protokoller kullanır. Model erişimi ve ücretler sağlayıcınıza bağlıdır. Kaydetmek mikrofon veya ekran açmaz.",11,UI.Muted));connection.Children.Add(new Expander{Header="Canlı ses yapılandırması",Content=realtime,Margin=new(0,14,0,8)});
        var row=new WrapPanel{Margin=new(0,12,0,8)};foreach(var button in new[]{AssistantWindow.Pill("Sağlayıcıyı kaydet",SaveProvider,true),AssistantWindow.Pill("Yapılandırmayı denetle",Check),AssistantWindow.Pill("Anahtarı unut",ForgetKey),AssistantWindow.Pill("Sağlayıcıyı + anahtarı kaldır",RemoveProvider)})row.Children.Add(button);connection.Children.Add(row);
        connectionEditor.Content=connection;page.Children.Add(connectionEditor);

        var prompt=new StackPanel();Label(prompt,"Asistan talimatları",instructions);instructions.AcceptsReturn=true;instructions.TextWrapping=TextWrapping.Wrap;instructions.Height=92;instructions.MaxLength=8000;instructions.Text=ai.Options.Instructions;prompt.Children.Add(AssistantWindow.Pill("Talimatları kaydet",()=>{ai.Options.Instructions=instructions.Text;SaveConfiguration();}));page.Children.Add(new Expander{Header="Asistan talimatları",Content=prompt,Margin=new(0,14,0,8)});
        status.Margin=new(0,12,0,0);page.Children.Add(status);
        page.Children.Add(UI.Text("Çentik AI sohbeti bellekte tutar. Sağlayıcılar konuşmaları saklayabilir; Temizle yalnızca yerel sohbeti kaldırır. Mikrofon, ekran paylaşımı ve Çentik AI bilgisayar denetimi ayrı oturum tercihleridir.",11,UI.Muted));
        Content=page;
        Reload();RefreshEnabled();
    }
    internal void ClearSecrets()=>key.Clear();
    private static void Label(Panel page,string title,UIElement control)
    {var label=UI.Text(title,11,UI.Muted);label.Margin=new(0,12,0,5);page.Children.Add(label);if(control is Control c)c.MinHeight=32;page.Children.Add(control);}
    private AssistantProvider? Selected=>list.SelectedItem as AssistantProvider;
    private void RefreshEnabled()=>enable.Content=ai.Options.Enabled?"AI yardımcısı açık":"AI yardımcısını etkinleştir";
    private void RefreshCapabilities(){vision.Content=imageInput?"Görüntü girişi etkin":"Görüntü girişi kapalı";tools.Content=toolInput?"İşlev araçları etkin":"İşlev araçları kapalı";}
    private void Reload(string? selectedId=null)
    {
        syncing=true;chatProvider.ItemsSource=ai.Options.Providers.ToArray();chatProvider.SelectedItem=ai.Provider;list.ItemsSource=ai.Options.Providers.ToArray();list.SelectedItem=ai.Options.Providers.FirstOrDefault(p=>p.Id==(selectedId??ai.Options.SelectedProvider))??ai.Options.Providers.FirstOrDefault();voiceProvider.ItemsSource=ai.Options.Providers.ToArray();voiceProvider.SelectedItem=ai.VoiceProvider;syncing=false;LoadSelected();
    }
    private void LoadSelected()
    {
        var selected=Selected;key.Clear();connectionEditor.Visibility=selected==null?Visibility.Collapsed:Visibility.Visible;
        list.IsEnabled=chatProvider.IsEnabled=voiceProvider.IsEnabled=ai.Options.Providers.Count>0;
        hermesHelp.Visibility=selected?.Backend=="Hermes"?Visibility.Visible:Visibility.Collapsed;tools.IsEnabled=selected?.Backend!="Hermes";
        if(selected==null){status.Text="Başlamak için bir hazır ayar seçin ve Sağlayıcı ekle'ye dokunun.";keyStatus.Text="";RefreshCapabilities();return;}
        name.Text=selected.Name;url.Text=selected.BaseUrl;model.Text=selected.Model;protocol.SelectedItem=selected.Protocol;realtimeProtocol.SelectedItem=selected.RealtimeProtocol;realtimeUrl.Text=selected.RealtimeUrl;realtimeModel.Text=selected.RealtimeModel;voiceName.Text=selected.Voice;key.Clear();imageInput=selected.Vision;toolInput=selected.Tools;RefreshCapabilities();
        try{keyStatus.Text=ai.Keys.Exists(selected.Id)?"Bu Windows kullanıcısı için bir anahtar kayıtlı. Korumak için boş bırakın.":"Kayıtlı anahtar yok.";}catch{keyStatus.Text="Windows kimlik bilgisi deposu kullanılamıyor.";}
    }
    private void AddProvider()
    {
        if(ai.Options.Providers.Count>=12){status.Text="En fazla 12 sağlayıcı tutun. Başka eklemeden önce birini kaldırın.";return;}
        var selected=AssistantProviderCatalog.Create(preset.SelectedItem?.ToString()??"OpenAI");
        if(selected.Name is "OpenAI" or "Google Gemini")
        {selected.RealtimeProtocol=selected.Name=="OpenAI"?"OpenAIRealtime":"GeminiLive";selected.RealtimeUrl=AssistantVoiceSession.DefaultUrl(selected.RealtimeProtocol);selected.Voice=selected.Name=="OpenAI"?"marin":"Kore";}
        ai.Options.Providers.Add(selected);if(ai.Options.SelectedProvider.Length==0)ai.Options.SelectedProvider=selected.Id;SaveConfiguration();Reload(selected.Id);
    }
    private AssistantProvider ReadFields()
    {
        var original=Selected??throw new InvalidOperationException("Önce bir sağlayıcı ekleyin.");
        var candidate=JsonSerializer.Deserialize<AssistantProvider>(JsonSerializer.Serialize(original))!;
        candidate.Name=name.Text.Trim();candidate.BaseUrl=url.Text.Trim();candidate.Model=model.Text.Trim();candidate.Protocol=protocol.SelectedItem?.ToString()??"ChatCompletions";candidate.Vision=imageInput;candidate.Tools=toolInput;candidate.RealtimeUrl=realtimeUrl.Text.Trim();candidate.RealtimeModel=realtimeModel.Text.Trim();candidate.RealtimeProtocol=realtimeProtocol.SelectedItem?.ToString()??"OpenAIRealtime";candidate.Voice=voiceName.Text.Trim();
        if(candidate.Name.Length==0)throw new InvalidOperationException("Bir sağlayıcı adı girin.");
        if(candidate.Backend=="Hermes"){candidate.Tools=false;if(candidate.Protocol!="ChatCompletions")throw new InvalidOperationException("Hermes Agent için ChatCompletions'ı seçin.");}
        if(candidate.BaseUrl.Length>0)AssistantTransport.ValidateEndpoint(candidate.BaseUrl);
        if(candidate.Model.Length==0 && candidate.RealtimeModel.Length==0)throw new InvalidOperationException("Bir sohbet modeli veya gerçek zamanlı model kimliği girin.");
        if(candidate.Model.Length>0 && candidate.BaseUrl.Length==0)throw new InvalidOperationException("Sohbet için bir API temel URL'si girin.");
        if(candidate.RealtimeUrl.Length>0)
        {if(!Uri.TryCreate(candidate.RealtimeUrl,UriKind.Absolute,out var endpoint) || endpoint.Scheme is not ("wss" or "ws") || endpoint.UserInfo.Length>0 || endpoint.Fragment.Length>0)throw new InvalidOperationException("Geçerli bir gerçek zamanlı WebSocket URL'si girin.");var validation=new UriBuilder(endpoint){Scheme=endpoint.Scheme=="wss"?"https":"http"};AssistantTransport.ValidateEndpoint(validation.Uri.AbsoluteUri);}
        return candidate;
    }
    private void SaveProvider()
    {
        try
        {
            var original=Selected!;var candidate=ReadFields();
            string savedId=ai.SaveProvider(original.Id,candidate,key.Password);key.Clear();Reload(savedId);status.Text="Sağlayıcı kaydedildi. Canlı istek için sohbeti kullanın; yapılandırma denetimleri hiçbir şey göndermez.";
        }
        catch(Exception ex){status.Text=ex.Message;}
    }
    private void Check(){try{ReadFields();status.Text="Yapılandırma geçerli görünüyor. Ağ isteği gönderilmedi; model desteği, kullandığınızda sağlayıcınız tarafından denetlenir.";}catch(Exception ex){status.Text=ex.Message;}}
    private void ForgetKey(){try{if(Selected is { } selected){ai.Keys.Remove(selected.Id);key.Clear();keyStatus.Text="Anahtar Windows Kimlik Bilgileri Yöneticisi'nden kaldırıldı.";}}catch(Exception ex){status.Text=ex.Message;}}
    private void RemoveProvider()
    {try{if(Selected is not { } selected)return;ai.Keys.Remove(selected.Id);ai.Options.Providers.Remove(selected);if(ai.Options.SelectedProvider==selected.Id)ai.Options.SelectedProvider=ai.Options.Providers.FirstOrDefault()?.Id??"";if(ai.Options.SelectedVoiceProvider==selected.Id)ai.Options.SelectedVoiceProvider="";SaveConfiguration();Reload();}catch(Exception ex){status.Text=ex.Message;}}
    private bool SaveConfiguration(){try{ai.SaveOptions();status.Text="Bu cihaza kaydedildi.";return true;}catch(Exception){status.Text="Sağlayıcı tercihleri kaydedilemedi. Yerel ayarlar klasörünü denetleyin.";return false;}}
}
