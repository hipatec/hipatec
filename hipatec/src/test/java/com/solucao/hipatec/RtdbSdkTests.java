package com.solucao.hipatec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.firebase.*;
import com.google.firebase.database.*;
import com.google.auth.oauth2.*;
import java.util.concurrent.*;
class RtdbSdkTests {
 @Test void transacaoRealRepetida() throws Exception {
  var host=System.getenv("FIREBASE_DATABASE_EMULATOR_HOST");
  assertTrue(java.util.Set.of("127.0.0.1:18900","127.0.0.1:18901").contains(host));
  var app=FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.create(new AccessToken("owner",new java.util.Date(Long.MAX_VALUE))))
   .setDatabaseUrl("http://"+host+"?ns=demo-hipatec-sdk-test").setProjectId("demo-hipatec-sdk-test").build(),"sdk-test");
  try {
   var ref=FirebaseDatabase.getInstance(app).getReference("posts");
   ref.setValueAsync(java.util.Map.of("texto","Post sintético","time",System.currentTimeMillis())).get(10,TimeUnit.SECONDS);
   var read=new CompletableFuture<DataSnapshot>();
   ref.addListenerForSingleValueEvent(new ValueEventListener(){public void onDataChange(DataSnapshot d){read.complete(d);}public void onCancelled(DatabaseError e){read.completeExceptionally(e.toException());}});
   var hash=com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON(read.get().getValue()).getHash();
   var response=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://"+host+"/posts.json?ns=demo-hipatec-sdk-test")).header("X-Firebase-ETag","true").header("Authorization","Bearer owner").build(),java.net.http.HttpResponse.BodyHandlers.discarding());
   assertEquals(hash,response.headers().firstValue("ETag").orElseThrow());
   for(int i=0;i<2;i++) {
    var done=new CompletableFuture<Void>();
    ref.runTransaction(new Transaction.Handler(){
     public Transaction.Result doTransaction(MutableData data){ data.child("valor").setValue(1);return Transaction.success(data); }
     public void onComplete(DatabaseError e,boolean commit,DataSnapshot data){if(e!=null)done.completeExceptionally(e.toException());else if(!commit)done.completeExceptionally(new AssertionError());else done.complete(null);}
    });
    done.get(10,TimeUnit.SECONDS);
   }
  } finally {app.delete();}
 }
}
