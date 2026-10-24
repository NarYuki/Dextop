package moe.n4tsu.dextop.deckaudio.probe;
import android.content.*;
import android.os.*;
import android.util.Log;
import java.util.concurrent.*;

/** Reads the advertised Car broker; does not request or alter the projection frame rate. */
public class CarBrokerProbeReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context receiverContext,Intent request){
    PendingResult pending=goAsync();Context context=receiverContext.getApplicationContext();
    new Thread(()->{
      CountDownLatch done=new CountDownLatch(1);
      ServiceConnection connection=new ServiceConnection(){public void onServiceDisconnected(ComponentName n){} public void onServiceConnected(ComponentName n,IBinder broker){
        Parcel p=Parcel.obtain(),reply=Parcel.obtain();
        try{
          Log.i("CarBrokerProbe","BROKER="+broker.getInterfaceDescriptor());
          p.writeInterfaceToken("com.google.android.gms.car.servicebroker.ICarServiceBroker");p.writeInt(1);
          Bundle info=new Bundle();info.putString("client_name","car-1-0");info.writeToParcel(p,0);
          broker.transact(1,p,reply,0);reply.readException();IBinder car=reply.readStrongBinder();
          Log.i("CarBrokerProbe","CAR="+(car==null?"null":car.getInterfaceDescriptor()));
          if(car!=null){
            Parcel m=Parcel.obtain(),r=Parcel.obtain();
            try{m.writeInterfaceToken("com.google.android.gms.car.ICar");car.transact(47,m,r,0);r.readException();IBinder windows=r.readStrongBinder();Log.i("CarBrokerProbe","WINDOWS="+(windows==null?"null":windows.getInterfaceDescriptor()));}
            finally{m.recycle();r.recycle();}
          }
          done.countDown();
        }catch(Exception e){Log.e("CarBrokerProbe","BROKER_FAILED",e);done.countDown();}
        finally{p.recycle();reply.recycle();}
      }};
      boolean bound=false;
      try{
        Intent intent=new Intent("com.google.android.gms.car.service.START").setComponent(new ComponentName("com.google.android.projection.gearhead","com.google.android.apps.auto.carservice.gmscorecompat.CarChimeraService"));
        bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);
        Log.i("CarBrokerProbe","BOUND="+bound);
        if(bound && !done.await(6,TimeUnit.SECONDS))Log.w("CarBrokerProbe","BROKER_TIMEOUT");
      }catch(Exception e){Log.e("CarBrokerProbe","PROBE_FAILED",e);}
      finally{if(bound)context.unbindService(connection);pending.finish();}
    },"CarBrokerProbe").start();
  }
}
