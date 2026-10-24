package moe.n4tsu.dextop.linux;
import android.os.ParcelFileDescriptor;
oneway interface ILinuxFifoResult {
    void complete(int error, in ParcelFileDescriptor descriptor);
}
