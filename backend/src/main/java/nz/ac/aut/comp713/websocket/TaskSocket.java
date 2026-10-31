package nz.ac.aut.comp713.websocket;
import jakarta.inject.Inject;
import jakarta.json.*;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import nz.ac.aut.comp713.service.AuthService;
import java.io.StringReader;
import java.util.concurrent.ConcurrentHashMap;
@ServerEndpoint("/ws/tasks")
public class TaskSocket {
 private static final ConcurrentHashMap<Session,String> CLIENTS=new ConcurrentHashMap<>();
 @Inject private AuthService auth;
 @OnOpen public void open(Session session){session.setMaxIdleTimeout(70000);session.setMaxTextMessageBufferSize(2048);}
 @OnMessage public void message(String text,Session session){
  try(JsonReader reader=Json.createReader(new StringReader(text))){
   JsonObject body=reader.readObject();String type=body.getString("type","");
   if(type.equals("auth")){String header="Bearer "+body.getString("token","");auth.authenticate(header);CLIENTS.put(session,header);session.getAsyncRemote().sendText("{\"type\":\"ready\"}");}
   else if(type.equals("ping")&&CLIENTS.containsKey(session)){auth.authenticate(CLIENTS.get(session));session.getAsyncRemote().sendText("{\"type\":\"pong\"}");}
   else close(session);
  }catch(Exception e){close(session);}
 }
 @OnClose public void closed(Session s){CLIENTS.remove(s);}
 @OnError public void error(Session s,Throwable error){CLIENTS.remove(s);}
 public static void changed(String type){String json=Json.createObjectBuilder().add("type",type).build().toString();CLIENTS.forEach((s,token)->{if(s.isOpen())s.getAsyncRemote().sendText(json,result->{if(!result.isOK())CLIENTS.remove(s);});else CLIENTS.remove(s);});}
 private void close(Session s){CLIENTS.remove(s);try{s.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY,"Sign in again."));}catch(Exception ignored){}}
}
