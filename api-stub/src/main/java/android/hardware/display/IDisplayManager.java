package android.hardware.display;

import android.media.projection.IMediaProjection;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.view.Surface;

public interface IDisplayManager extends IInterface {
    // API < 30
    int createVirtualDisplay(IVirtualDisplayCallback callback, IMediaProjection projection, String packageName, String name, int width, int height, int densityDpi, Surface surface, int flags, String uniqueId);

    // API >= 30
    int createVirtualDisplay(VirtualDisplayConfig config, IVirtualDisplayCallback callback, IMediaProjection projection, String packageName);

    void releaseVirtualDisplay(IVirtualDisplayCallback callback);

    abstract class Stub extends Binder implements IDisplayManager {
        public static IDisplayManager asInterface(IBinder binder) {
            throw new RuntimeException("STUB");
        }
    }
}
