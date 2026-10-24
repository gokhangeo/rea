using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace Centik;

public sealed partial class OverlayWindow
{
    private readonly StackPanel monitorRows=new();
    private string monitorTab="overview";
    private ScrollViewer? monitorDetailScroll;
    private readonly Dictionary<string,Button> monitorTabs=new();
    internal string MonitorTab=>monitorTab;
    internal void SelectMonitorTab(string id)
    {
        if(!monitorTabs.ContainsKey(id))return;if(monitorTab!=id){monitorGeneration++;monitorReadAt=0;monitorPrevious=monitorLatest=null;if(id=="hardware")HardwareMonitor.ResetSampling();}monitorTab=id;RenderSystemMonitor();monitorDetailScroll?.ScrollToTop();
        foreach(var pair in monitorTabs){pair.Value.Background=pair.Key==id?UI.Brush(app.Settings.SelectionFill):Brushes.Transparent;pair.Value.Foreground=pair.Key==id?UI.ContrastText(app.Settings.SelectionFill):UI.Muted;}
        UpdateSystemMonitor();
    }
    private readonly Queue<double?> monitorCpuHistory=new();
    private MonitorSnapshot? monitorPrevious,monitorLatest;
    internal bool MonitorPolling=>monitorActive;
    internal bool MonitorReady=>monitorLatest!=null && monitorPrevious!=null;
    internal bool MonitorBusy=>monitorBusy;
    internal int MonitorReads {get;private set;}
    internal int MonitorResults {get;private set;}
    internal int MonitorRejected {get;private set;}
    internal Task? MonitorDeliveryGate {get;set;}
    private bool monitorBusy,monitorActive,monitorClosed;
    private int monitorGeneration;
    private long monitorReadAt;

    internal static void DetachMonitorRows(StackPanel rows)
    {
        if(rows.Parent is ScrollViewer owner)owner.Content=null;
        else if(rows.Parent is Panel parent)parent.Children.Remove(rows);
    }

    private FrameworkElement BuildSystemMonitor()
    {
        DetachMonitorRows(monitorRows);
        var panel=new Grid();panel.RowDefinitions.Add(new(){Height=GridLength.Auto});panel.RowDefinitions.Add(new());
        var tabs=new WrapPanel{Margin=new(0,0,0,10)};monitorTabs.Clear();
        foreach(var (id,label) in new[]{("overview","Genel bakış"),("hardware","Donanım"),("network","Ağ"),("storage","Depolama"),("processes","İşlemler")})
        {
            if(id=="network" && !app.Settings.MonitorNetwork || id=="storage" && !app.Settings.MonitorStorage || id=="processes" && !app.Settings.MonitorProcesses)continue;
            var button=UI.Button(label,()=>SelectMonitorTab(id));button.FontSize=10;button.Padding=new(7,5,7,5);button.MinHeight=28;button.Margin=new(0,0,3,3);UI.Named(button,"Monitor "+label);monitorTabs[id]=button;tabs.Children.Add(button);
        }
        panel.Children.Add(tabs);monitorDetailScroll=UI.Scroll(monitorRows);monitorDetailScroll.VerticalScrollBarVisibility=ScrollBarVisibility.Hidden;ScrollChrome.SetEnabled(monitorDetailScroll,false);monitorDetailScroll.Padding=new(0,0,2,0);UI.Named(monitorDetailScroll,"System details · scroll or use Page Down");
        tabs.Measure(new Size(Math.Max(1,bodyWidth),double.PositiveInfinity));
        double reserved=(WidgetOn("clock")?80:0)+49+tabs.DesiredSize.Height;
        monitorDetailScroll.Height=Math.Clamp((app.Settings.PanelMaxHeight-app.Settings.Height-32)/app.Settings.PanelScale-reserved,92,240);
        bodyReflow.Add(()=>{tabs.Measure(new Size(Math.Max(1,bodyWidth),double.PositiveInfinity));double reserved=(WidgetOn("clock")?80:0)+49+tabs.DesiredSize.Height;monitorDetailScroll.Height=Math.Clamp((app.Settings.PanelMaxHeight-app.Settings.Height-32)/app.Settings.PanelScale-reserved,92,240);});
        Grid.SetRow(monitorDetailScroll,1);panel.Children.Add(monitorDetailScroll);SelectMonitorTab(monitorTabs.ContainsKey(monitorTab)?monitorTab:"overview");return panel;
    }

    private bool SystemMonitorVisible=>!monitorClosed && expanded && (app.Settings.CompactLayout || hoverPanelWidget=="system") && !commandOpen && activePage=="status" && WidgetOn("system") && IsVisible && monitorRows.IsVisible && monitorRows.IsDescendantOf(body);

    private void InvalidateSystemMonitor()
    {
        monitorActive=false;monitorGeneration++;monitorPrevious=null;monitorLatest=null;monitorCpuHistory.Clear();monitorReadAt=0;HardwareMonitor.ResetSampling();
    }

    private async void UpdateSystemMonitor()
    {
        if(!SystemMonitorVisible)
        {
            if(monitorActive)InvalidateSystemMonitor();
            return;
        }
        if(!monitorActive)HardwareMonitor.ResetSampling();
        monitorActive=true;
        if(monitorBusy || (monitorReadAt!=0 && Stopwatch.GetElapsedTime(monitorReadAt).TotalSeconds<2))return;
        monitorBusy=true;MonitorReads++;monitorReadAt=Stopwatch.GetTimestamp();int generation=monitorGeneration;
        bool network=monitorTab=="network" && app.Settings.MonitorNetwork,storage=monitorTab=="storage" && app.Settings.MonitorStorage,processes=monitorTab=="processes" && app.Settings.MonitorProcesses;bool hardware=monitorTab is "overview" or "hardware",gpuOnly=monitorTab=="overview";
        try
        {
            var snapshot=await Task.Run(()=>SystemMonitor.Capture(network,storage,processes,processes,hardware,gpuOnly));
            // Diagnostics can hold delivery to exercise navigation during an in-flight read.
            if(DiagnosticRun && MonitorDeliveryGate is Task gate)await Task.WhenAny(gate,Task.Delay(3000));
            // A quick return to Status must not revive a read from the detached body.
            if(generation!=monitorGeneration || !SystemMonitorVisible){MonitorRejected++;return;}
            MonitorResults++;
            monitorCpuHistory.Enqueue(SystemMonitor.Cpu(snapshot,monitorLatest));while(monitorCpuHistory.Count>30)monitorCpuHistory.Dequeue();
            monitorPrevious=monitorLatest;monitorLatest=snapshot;RenderSystemMonitor();Dimensions();if(Math.Abs(PaintedHeight-expandedHeight)>.5)motion.Go(surface,IslandSurface.SurfaceHeightProperty,expandedHeight,Math.Min(300,app.Settings.AnimationMs));
        }
        catch
        {
            if(generation==monitorGeneration && SystemMonitorVisible){monitorRows.Children.Clear();monitorRows.Children.Add(UI.Text("Sistem bilgileri kullanılamıyor. Yeniden deneniyor…",11,UI.Muted));}
        }
        finally{monitorBusy=false;if(generation!=monitorGeneration && SystemMonitorVisible)UpdateSystemMonitor();}
    }

    private void StopSystemMonitor(){monitorClosed=true;InvalidateSystemMonitor();}

    private void RenderSystemMonitor()
    {
        BeginMonitorRender();var s=monitorLatest;
        if(s==null){AddMonitorElement(UI.Text("Sistem bilgileri örnekleniyor…",11,UI.Muted));CommitMonitorRender();return;}
        if(monitorTab=="overview")
        {
        double? cpu=SystemMonitor.Cpu(s,monitorPrevious);
        MonitorRow("CPU",cpu.HasValue?$"{cpu:0.#}%":"Örnekleniyor…",true);
        monitorHistory.SetValues(monitorCpuHistory,UI.Brush(app.Settings.Accent));AddMonitorElement(monitorHistory);
        AddMonitorElement(UI.Text("CPU · 60 saniyelik pencere · 2 saniyelik örnekler",10,UI.Muted));
        if(s.Total is ulong total && s.Available is ulong available)
        {
            MonitorRow("Bellek",SystemMonitor.Bytes(total-available)+" / "+SystemMonitor.Bytes(total),true);
            UpdateMonitorMemory(total,available);AddMonitorElement(monitorMemoryTrack);
        }
        else MonitorRow("Fiziksel bellek","Kullanılamıyor");
        if(s.Hardware?.Gpus.Any(g=>g.Utilization.HasValue)==true)MonitorRow("GPU",HardwareMonitor.Percent(s.Hardware.Gpus.Where(g=>g.Utilization.HasValue).Max(g=>g.Utilization)),true);
        MonitorRow("Sistem çalışma süresi",SystemMonitor.Uptime(s.Uptime));
        }
        if(monitorTab=="network" && app.Settings.MonitorNetwork)
        {
            MonitorSection("AĞ · ALMA / GÖNDERME");
            double seconds=monitorPrevious==null?0:(s.Timestamp-monitorPrevious.Timestamp)/(double)Stopwatch.Frequency;
            foreach(var net in s.Networks)
            {
                var before=monitorPrevious?.Networks.FirstOrDefault(n=>n.Id==net.Id);
                MonitorRow(net.Name,SystemMonitor.RateText(SystemMonitor.Rate(net.Received,before?.Received,seconds))+" ↓");
                MonitorRow("",SystemMonitor.RateText(SystemMonitor.Rate(net.Sent,before?.Sent,seconds))+" ↑");
            }
            if(s.Networks.Count==0)MonitorRow("Etkin arayüzler","Kullanılabilir yok");
        }
        if(monitorTab=="storage" && app.Settings.MonitorStorage)
        {
            MonitorSection("SABİT BİRİMLER · BOŞ / TOPLAM");
            foreach(var drive in s.Drives)MonitorRow(drive.Name,SystemMonitor.Bytes(drive.Free)+" / "+SystemMonitor.Bytes(drive.Total));
            if(s.Drives.Count==0)MonitorRow("Birimler","Kullanılabilir yok");
        }
        if(monitorTab=="processes" && app.Settings.MonitorProcesses)
        {
            MonitorRow("İşlemler / iş parçacıkları",SystemMonitor.ProcessCounts(s.ProcessCount,s.ThreadCount,s.PartialThreads));
            MonitorSection("EN ÇOK İŞLEM · ÇALIŞMA KÜMESİ");
            foreach(var process in s.Processes)MonitorRow(process.Name+" · "+process.Id,SystemMonitor.Bytes(process.WorkingSet));
            if(s.Processes.Count==0)MonitorRow("İşlemler","Kullanılamıyor");
            var note=UI.Text("Çalışma kümeleri belleği paylaşabilir; toplam RAM kullanımına eşit değildir.",10,UI.Muted);note.Margin=new(0,6,0,0);AddMonitorElement(note);
        }
        if(monitorTab=="hardware")RenderHardware(s.Hardware);
        if(s.Errors.Length>0){var errors=UI.Text(string.Join(" · ",s.Errors),10,UI.Muted);errors.Margin=new(0,8,0,0);AddMonitorElement(errors);}
        CommitMonitorRender();ApplyHeadingTypography(monitorRows);
    }

    private void RenderHardware(MonitorHardware? hardware)
    {
        if(hardware==null){MonitorRow("Donanım","Kullanılamıyor");return;}
        MonitorSection("İŞLEMCİ");
        foreach(var cpu in hardware.Cpus)
        {
            var model=UI.Heading(cpu.Name.Length>0?cpu.Name:"İşlemci kullanılamıyor",12);model.TextWrapping=TextWrapping.NoWrap;model.TextTrimming=TextTrimming.CharacterEllipsis;model.ToolTip=cpu.Name;AddMonitorElement(model);
            MonitorRow("Çekirdekler / iş parçacıkları",(cpu.Cores?.ToString()??"—")+" / "+(cpu.Threads?.ToString()??"—")+(cpu.MaxClock is double mhz && mhz>0?$" · {mhz:0} MHz":""));
        }
        if(hardware.Cpus.Count==0)MonitorRow("CPU ayrıntıları","Kullanılamıyor");
        MonitorSection("GRAFİK");
        foreach(string model in hardware.GpuModels){var text=UI.Heading(model,12);text.TextWrapping=TextWrapping.NoWrap;text.TextTrimming=TextTrimming.CharacterEllipsis;text.ToolTip=model;text.Margin=new(0,0,0,4);AddMonitorElement(text);}
        if(hardware.GpuModels.Count==0)MonitorRow("GPU modeli","Kullanılamıyor");
        for(int i=0;i<hardware.Gpus.Count;i++)
        {
            var gpu=hardware.Gpus[i];
            // WMI controller names have no reliable LUID mapping: keep counter identities explicit.
            MonitorRow(hardware.Gpus.Count>1?"GPU sayacı "+(i+1):"GPU kullanımı",HardwareMonitor.Percent(gpu.Utilization),true);
            MonitorRow("Ayrılmış bellek",gpu.Dedicated is ulong dedicated?SystemMonitor.Bytes(dedicated):"Kullanılamıyor");
        }
        if(hardware.Gpus.Count==0)
        {
            MonitorRow("GPU kullanımı","Kullanılamıyor");MonitorRow("Ayrılmış / paylaşılan bellek","Kullanılamıyor");
        }
        if(hardware.Sensors.Count>0){MonitorSection("SICAKLIKLAR VE FANLAR");foreach(var sensor in hardware.Sensors)MonitorRow(sensor.Name,HardwareMonitor.SensorValue(sensor));}
        var technical=new StackPanel();
        foreach(var gpu in hardware.Gpus){technical.Children.Add(UI.Text("Sayaç: "+gpu.Id,10,UI.Muted));technical.Children.Add(UI.Text("Paylaşılan bellek: "+(gpu.Shared is ulong shared?SystemMonitor.Bytes(shared):"Kullanılamıyor"),11,UI.Muted));}
        technical.Children.Add(UI.Text("Kullanım en yoğun GPU motorunu temel alır. Sayaç kimlikleri yukarıdaki modeli tanımlamaz.",11,UI.Muted));
        technical.Children.Add(UI.Text(hardware.Sensors.Count>0?"Sensörler: "+string.Join(" / ",hardware.Sensors.Select(s=>s.Provider).Distinct()):"Sıcaklık ve fan sensörleri, halihazırda çalışan bir LibreHardwareMonitor veya OpenHardwareMonitor WMI sağlayıcısı gerektirir.",11,UI.Muted));
        if(hardware.Status!=null)technical.Children.Add(UI.Text(hardware.Status,10,UI.Muted));
        if(monitorTechnical==null){monitorTechnical=new Expander{Header="Sensör kullanılabilirliği ve teknik ayrıntılar",Foreground=UI.Muted,FontSize=11,Margin=new(0,14,0,0)};monitorTechnical.Expanded+=(_,_)=>hardwareDetailsOpen=true;monitorTechnical.Collapsed+=(_,_)=>hardwareDetailsOpen=false;}
        monitorTechnical.Content=technical;monitorTechnical.IsExpanded=hardwareDetailsOpen;AddMonitorElement(monitorTechnical);
    }
    private bool hardwareDetailsOpen;
    private Expander? monitorTechnical;

    private void MonitorSection(string title){var label=UI.Label(title);label.Margin=new(0,15,0,7);AddMonitorElement(label);}
    private void MonitorRow(string label,string value,bool prominent=false)
    {
        var row=GetMonitorRow();var name=(TextBlock)row.Children[0];var text=(TextBlock)row.Children[1];
        Motion.Text(name,label);name.ToolTip=label;Motion.Text(text,value);text.FontSize=prominent?15:11;text.MaxWidth=Math.Max(90,bodyWidth*.61);text.ToolTip=value;AddMonitorElement(row);
    }
}
