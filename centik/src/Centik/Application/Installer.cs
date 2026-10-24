using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using Microsoft.Win32;

namespace Centik;

internal static class Installer
{
    internal static string InstallDirectory=>Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","Centik");
    internal static string ShortcutPath=>Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),"Centik.lnk");
    private const string UninstallKey=@"Software\Microsoft\Windows\CurrentVersion\Uninstall\LumaIsland";
    public static void Run(string[] args)
    {
        bool silent=args.Contains("--silent");
        if(silent)
        {
            try {Install();if(args.Contains("--launch"))Launch();}
            catch(Exception ex) {File.WriteAllText(Path.Combine(Path.GetDirectoryName(Environment.ProcessPath)!,"install-error.txt"),ex.ToString());Environment.ExitCode=1;}
            return;
        }
        var application=new Application();application.Resources.MergedDictionaries.Add(new ResourceDictionary{Source=new Uri("/Centik;component/Theme.xaml",UriKind.Relative)});
        var window=new Window{Title="Centik Kurulumu",Width=560,Height=440,ResizeMode=ResizeMode.NoResize,WindowStartupLocation=WindowStartupLocation.CenterScreen};
        window.Icon=System.Windows.Media.Imaging.BitmapFrame.Create(new Uri("pack://application:,,,/Assets/island.ico"));window.SourceInitialized+=(_,_)=>Native.DarkTitleBar(window);
        var panel=new StackPanel{Margin=new(36)};panel.Children.Add(UI.Text("Çentik",36,UI.Brush("#B4F272")));panel.Children.Add(UI.Text("Küçük bir alan. Daha fazla olasılık.",15,UI.Muted));
        panel.Children.Add(new Border{Height=26});panel.Children.Add(UI.Text("Müzik, odak ve günlük araçlarınız — her zaman yakın.\n\nYalnızca bu Windows kullanıcısı için kurulur. Otomatik başlatma varsayılan olarak kapalıdır.",14));
        var state=UI.Text("Konum: "+InstallDirectory,11,UI.Muted);state.Margin=new(0,20,0,12);panel.Children.Add(state);
        var launch=new CheckBox{Content="Kurulumdan sonra Centik'i aç",IsChecked=true};panel.Children.Add(launch);
        var button=UI.Button("Centik'i Kur",()=>{},true);button.Click+=(_,_)=>
        {
            button.IsEnabled=false;
            try {Install();if(launch.IsChecked==true)Launch();window.Close();}
            catch(Exception ex) {state.Text="Kurulum başarısız: "+ex.Message;button.IsEnabled=true;}
        };panel.Children.Add(button);window.Content=panel;application.Run(window);
    }
    private static void Launch()=>Process.Start(new ProcessStartInfo(Path.Combine(InstallDirectory,"Centik.exe")){UseShellExecute=true});
    internal static void Install()
    {
        var target=Path.GetFullPath(InstallDirectory);var expected=Path.GetFullPath(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"Programs","Centik"));
        if(!string.Equals(target,expected,StringComparison.OrdinalIgnoreCase))throw new IOException("Geçersiz kurulum konumu.");
        foreach(string processName in new[]{"LumaIsland","BillyNabilaIsland","Centik"})
        foreach(var process in Process.GetProcessesByName(processName))
        {
            using(process)
            {
                try {if(new[]{"LumaIsland.exe","BillyNabilaIsland.exe","Centik.exe"}.Any(name=>string.Equals(process.MainModule?.FileName,Path.Combine(target,name),StringComparison.OrdinalIgnoreCase)))throw new IOException("Güncellemeden önce Centik'i sistem tepsisinden kapatın.");}
                catch(System.ComponentModel.Win32Exception) { }
            }
        }
        Directory.CreateDirectory(target);
        string source=Environment.ProcessPath!;string executable=Path.Combine(target,"Centik.exe");
        if(!Path.GetFileName(source).StartsWith("Centik",StringComparison.OrdinalIgnoreCase))throw new IOException("Uygulamayı kurmak için Centik-Setup.exe kullanın.");
        if(!string.Equals(source,executable,StringComparison.OrdinalIgnoreCase))
        {
            File.Copy(source,executable+".new",true);File.Move(executable+".new",executable,true);
        }
        File.WriteAllText(Path.Combine(target,"Uninstall.ps1"),Uninstaller);
        foreach(var name in new[]{"LICENSE","LUCIDE-LICENSE.txt","NAudio-LICENSE.txt","DOTNET-LICENSE.txt","DOTNET-ThirdPartyNotices.txt"})
        {
            using var license=typeof(Installer).Assembly.GetManifestResourceStream("Centik."+name);
            if(license!=null){using var reader=new StreamReader(license);File.WriteAllText(Path.Combine(target,name),reader.ReadToEnd());}
        }
        object? shell=null,shortcut=null;
        try
        {
            var type=Type.GetTypeFromProgID("WScript.Shell") ?? throw new IOException("Windows kısayol hizmeti kullanılamıyor.");
            shell=Activator.CreateInstance(type);dynamic dynamicShell=shell!;shortcut=dynamicShell.CreateShortcut(ShortcutPath);dynamic link=shortcut;
            link.TargetPath=executable;link.WorkingDirectory=target;link.Description="Windows için Centik";link.IconLocation=executable+",0";link.Save();
            foreach(string legacyName in new[]{"Luma Island.lnk","BillyNabila Island.lnk"})
            {
            string legacyShortcut=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs),legacyName);
            if(File.Exists(legacyShortcut))
            {
                object old=dynamicShell.CreateShortcut(legacyShortcut);
                try {dynamic previous=old;string oldTarget=Path.GetFullPath((string)previous.TargetPath);if(new[]{"LumaIsland.exe","BillyNabilaIsland.exe","Centik.exe"}.Any(name=>string.Equals(oldTarget,Path.Combine(target,name),StringComparison.OrdinalIgnoreCase)))File.Delete(legacyShortcut);}
                finally{if(Marshal.IsComObject(old))Marshal.FinalReleaseComObject(old);}
            }
            }
        }
        finally {if(shortcut!=null)Marshal.FinalReleaseComObject(shortcut);if(shell!=null)Marshal.FinalReleaseComObject(shell);}
        using var key=Registry.CurrentUser.CreateSubKey(UninstallKey);
        key.SetValue("DisplayName","Çentik");key.SetValue("DisplayVersion",typeof(App).Assembly.GetName().Version!.ToString(3));key.SetValue("Publisher","Çentik");key.SetValue("InstallLocation",target);key.SetValue("DisplayIcon",executable);
        key.SetValue("UninstallString","powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File \""+Path.Combine(target,"Uninstall.ps1")+"\"");
        key.SetValue("QuietUninstallString","powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File \""+Path.Combine(target,"Uninstall.ps1")+"\" -Quiet");
        key.SetValue("NoModify",1,RegistryValueKind.DWord);key.SetValue("NoRepair",1,RegistryValueKind.DWord);key.SetValue("EstimatedSize",(int)(new FileInfo(executable).Length/1024),RegistryValueKind.DWord);
        // On update, retain autostart preference while updating a prior executable path to the installed copy.
        using var startup=Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run",true);
        if(startup?.GetValue("LumaIsland")!=null || startup?.GetValue("BillyNabilaIsland")!=null || startup?.GetValue("Centik")!=null)
        {
            startup!.SetValue("Centik","\""+executable+"\" --background");startup.DeleteValue("LumaIsland",false);startup.DeleteValue("BillyNabilaIsland",false);
        }
        foreach(string legacyName in new[]{"LumaIsland.exe","BillyNabilaIsland.exe"})
        {
        string legacyExecutable=Path.Combine(target,legacyName);
        if(File.Exists(legacyExecutable) && !string.Equals(source,legacyExecutable,StringComparison.OrdinalIgnoreCase))File.Delete(legacyExecutable);
        }
    }
    private const string Uninstaller="""
param([switch]$Quiet)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$islandTarget = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Programs\LumaIsland'))
$scriptFolder = [IO.Path]::GetFullPath($PSScriptRoot)
if ($scriptFolder -ne $islandTarget) { throw 'Invalid uninstaller location.' }
if (-not $Quiet) {
    $answer = [Windows.Forms.MessageBox]::Show('Uninstall Centik? Your personal preferences will be kept.', 'Centik', 'YesNo', 'Question')
    if ($answer -ne 'Yes') { exit 0 }
}
Get-Process -Name LumaIsland,BillyNabilaIsland,Centik -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Path -and @((Join-Path $islandTarget 'LumaIsland.exe'), (Join-Path $islandTarget 'BillyNabilaIsland.exe'), (Join-Path $islandTarget 'Centik.exe')) -contains [IO.Path]::GetFullPath($_.Path)) { Stop-Process -Id $_.Id -Force }
}
Start-Sleep -Milliseconds 700
$startupKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
foreach ($valueName in @('LumaIsland','BillyNabilaIsland','Centik')) {
    $startupValue = (Get-ItemProperty -LiteralPath $startupKey -Name $valueName -ErrorAction SilentlyContinue).$valueName
    if ($startupValue -and $startupValue.Contains($islandTarget)) { Remove-ItemProperty -LiteralPath $startupKey -Name $valueName }
}
$shortcutFile = Join-Path ([Environment]::GetFolderPath('Programs')) 'Centik.lnk'
if (Test-Path -LiteralPath $shortcutFile) { Remove-Item -LiteralPath $shortcutFile -Force }
$uninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\LumaIsland'
if (Test-Path -LiteralPath $uninstallKey) { Remove-Item -LiteralPath $uninstallKey -Force }
# Only the exact dedicated application directory may be removed. Preferences live elsewhere.
if ($islandTarget -ne [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Programs\LumaIsland'))) { throw 'Invalid removal location.' }
Remove-Item -LiteralPath $islandTarget -Recurse -Force
if (-not $Quiet) { [Windows.Forms.MessageBox]::Show('Centik has been uninstalled.','Centik') | Out-Null }
""";
}
