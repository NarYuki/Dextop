# Compatibility

## Samsung DeX

Mostly supported and currently the most complete environment. Dextop treats DeX as platform-managed and avoids applying generic freeform settings that could conflict with Samsung's desktop implementation.

## Google Pixel

Limited and incomplete. Behavior depends on the Android release, freeform/desktop implementation, and hidden API availability.

## Other devices

Experimental. Dextop has profiles for major OEM families, but a vendor profile does not guarantee that every model or firmware implements the required services.

At runtime Dextop probes APIs and tries ordered mirror and windowing strategies. A model-specific workaround must be isolated by manufacturer, model, codename, fingerprint prefix, and SDK range.

See [Adding device support](https://github.com/NarYuki/Dextop/blob/main/docs/ADDING_DEVICE_SUPPORT.en.md) for implementation and validation requirements.
