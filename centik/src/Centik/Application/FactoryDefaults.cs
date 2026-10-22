using System;
using System.IO;

namespace Centik;

// Applied only to absent/unreadable preferences and explicit preference reset.
// Persisted profiles retain their historical deserialization/migration behavior.
internal static class FactoryDefaults
{
    internal static Settings Create()
    {
        using var stream=typeof(FactoryDefaults).Assembly.GetManifestResourceStream("Centik.DefaultLayout.json")
            ?? throw new InvalidOperationException("The bundled default layout is missing.");
        using var reader=new StreamReader(stream);
        var settings=new Settings();
        CustomizationProfile.Import(reader.ReadToEnd(),settings);
        return settings;
    }
}
