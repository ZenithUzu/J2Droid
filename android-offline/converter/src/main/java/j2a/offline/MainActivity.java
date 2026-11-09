package j2a.offline;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Android-only UI: choose JAR (+ optional JAD), convert locally, save APK. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private Button pickJar,pickJad,clearJad,convert,save;
    private TextView selected,status,log;
    private ProgressBar progress;
    private boolean copying;
    private String backupPassword="";
    private String previousLog="";
    private final Runnable poll=new Runnable(){public void run(){refresh();ui.postDelayed(this,700);}};
    private int accent(){return Build.VERSION.SDK_INT>=31?getColor(android.R.color.system_accent1_500):0xff536dfe;}
    private int surface(){return Build.VERSION.SDK_INT>=31?getColor(android.R.color.system_neutral1_50):0xfff7f8fc;}
    private int ink(){return Build.VERSION.SDK_INT>=31?getColor(android.R.color.system_neutral1_900):0xff1b1b21;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(LinearLayout parent,String label,View.OnClickListener action){Button b=new Button(this);b.setText(label);b.setTextColor(accent());b.setAllCaps(false);b.setOnClickListener(action);parent.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));return b;}
    private static final int BG=0xff14111e, PANEL=0xff252032, ITEM=0xff342d43, WHITE=0xfff6f1ff, MUTED=0xffbcb0cf, LAVENDER=0xffcbb4ff;
    private LinearLayout content,nav;
    private TextView fileDetails;
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String text,int size,boolean bold){TextView v=text(text,size,WHITE,bold);return v;}
    private void margin(LinearLayout parent,View child,int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.topMargin=dp(top);parent.addView(child,p);}
    private TextView tile(String title,String subtitle,View.OnClickListener action){
        LinearLayout wrapper=column();wrapper.setPadding(dp(17),dp(12),dp(16),dp(12));wrapper.setBackground(shape(ITEM,18));
        TextView a=label(title,15,true);wrapper.addView(a);if(subtitle!=null){TextView b=text(subtitle,13,MUTED,false);wrapper.addView(b);}
        wrapper.setOnClickListener(action);wrapper.setMinimumHeight(dp(64));
        TextView placeholder=new TextView(this); // tile text is represented by its parent
        placeholder.setTag(wrapper);return placeholder;
    }
    private LinearLayout entry(String title,String subtitle,View.OnClickListener action){
        LinearLayout l=column();l.setPadding(dp(17),dp(13),dp(17),dp(13));l.setBackground(shape(ITEM,17));
        l.addView(label(title,15,true));if(subtitle!=null)l.addView(text(subtitle,13,MUTED,false));l.setMinimumHeight(dp(62));l.setOnClickListener(action);return l;
    }
    private TextView action(String title,boolean primary,View.OnClickListener click){
        TextView v=text(title,primary?18:14,primary?0xff241734:WHITE,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(shape(primary?LAVENDER:ITEM,primary?28:16));v.setMinimumHeight(dp(primary?58:48));v.setOnClickListener(click);return v;
    }
    private void section(String title){TextView v=text(title,17,LAVENDER,true);margin(content,v,-2,19);}
    private void showPage(int page){
        content.removeAllViews();
        nav.removeAllViews();
        if(page==0)showConvertPage();else if(page==1)showProjectsPage();else showHistoryPage();
        String[] names={"⌂  Convertir","▱  Proyectos","◷  Historial"};
        for(int i=0;i<3;i++){final int n=i;TextView t=text(names[i],13,i==page?LAVENDER:MUTED,i==page);t.setGravity(Gravity.CENTER);t.setPadding(dp(4),dp(17),dp(4),dp(17));t.setOnClickListener(v->showPage(n));nav.addView(t,new LinearLayout.LayoutParams(0,dp(60),1));}
        if(page==0)refresh();
    }
    private void showConvertPage(){
        LinearLayout header=horizontal();
        TextView title=label("☕  J2Droid",26,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=action("⚙",false,v->showSettings());header.addView(settings,new LinearLayout.LayoutParams(dp(52),dp(48)));
        margin(content,header,-2,4);
        TextView subtitle=text("Java ME → Android",13,MUTED,false);margin(content,subtitle,-2,0);
        fileDetails=text("Seleccionar juego JAR",16,WHITE,true);
        LinearLayout file=column();file.setPadding(dp(18),dp(16),dp(18),dp(16));file.setBackground(shape(PANEL,20));file.addView(fileDetails);
        file.addView(text("Toca para elegir un archivo",13,MUTED,false));file.setOnClickListener(v->pick(1));margin(content,file,-2,23);
        section("Configuración");
        margin(content,entry("Nombre del juego","Se detecta automáticamente del JAR",v->showProjects()),-2,12);
        margin(content,entry("ID del paquete","Se genera a partir de los metadatos del juego",v->showProjects()),-2,8);
        section("Firma del juego");
        LinearLayout signing=horizontal();signing.setPadding(dp(10),dp(10),dp(10),dp(10));signing.setBackground(shape(PANEL,18));
        TextView signingLabel=text("🔑  Firma local",14,WHITE,true);signing.addView(signingLabel,new LinearLayout.LayoutParams(0,-2,1));
        TextView backup=action("Respaldar",false,v->askBackup(false));signing.addView(backup);
        TextView restore=action("Restaurar",false,v->askBackup(true));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-2,-2);rp.leftMargin=dp(6);signing.addView(restore,rp);
        margin(content,signing,-2,10);
        section("Partidas (RMS)");
        margin(content,entry("Carpeta Java","Se configura dentro de cada juego convertido",v->new android.app.AlertDialog.Builder(this).setTitle("Partidas RMS").setMessage("Cada juego convertido permite elegir su carpeta Java desde el menú del propio juego. Esta opción no se configura desde J2Droid.").setPositiveButton("Entendido",null).show()),-2,10);
        convert=new Button(this);convert.setText("✦  Crear APK");convert.setTextSize(18);convert.setAllCaps(false);convert.setTextColor(0xff241734);convert.setBackground(shape(LAVENDER,28));convert.setOnClickListener(v->startBuild());margin(content,convert,62,23);
        save=new Button(this);save.setText("Guardar APK");save.setAllCaps(false);save.setTextColor(WHITE);save.setBackground(shape(ITEM,17));save.setOnClickListener(v->saveApk());margin(content,save,48,10);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);margin(content,progress,5,12);
        status=text("",13,MUTED,false);margin(content,status,-2,10);
        log=text("",12,MUTED,false);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);margin(content,log,-2,8);
        selected=fileDetails;pickJar=convert;pickJad=convert;clearJad=convert;
    }
    private void showSettings(){
        String[] options={"Seleccionar JAD (opcional)","Quitar JAD","Información de firma"};
        new android.app.AlertDialog.Builder(this).setTitle("Opciones avanzadas").setItems(options,(d,which)->{
            if(which==0)pick(2);
            else if(which==1){getPreferences(0).edit().remove("jad").apply();Toast.makeText(this,"JAD quitado",Toast.LENGTH_SHORT).show();}
            else new android.app.AlertDialog.Builder(this).setTitle("Firma de juegos").setMessage("Respalda la identidad de firma antes de borrar los datos de J2Droid. Sin ella no podrás actualizar juegos ya firmados con esa clave.").setPositiveButton("Cerrar",null).show();
        }).setNegativeButton("Cerrar",null).show();
    }
    private void showProjectsPage(){
        margin(content,label("Proyectos",25,true),-2,8);
        String jar=getPreferences(0).getString("jar","");
        if(jar.isEmpty())margin(content,entry("Todavía no hay juego seleccionado","Selecciona un JAR para ver sus datos",v->showPage(0)),-2,18);
        else margin(content,entry(getPreferences(0).getString("name","Juego.jar"),"Ver información del juego",v->showProjects()),-2,18);
        margin(content,action("Seleccionar JAR",true,v->pick(1)),55,18);
    }
    private void showHistoryPage(){
        margin(content,label("Historial",25,true),-2,8);
        SharedPreferences state=getSharedPreferences(BuildService.STATE,0);
        try{
            org.json.JSONArray history=new org.json.JSONArray(state.getString("history","[]"));
            if(history.length()==0)margin(content,text("Todavía no hay conversiones.",14,MUTED,false),-2,20);
            for(int i=history.length()-1;i>=0;i--){
                org.json.JSONObject entry=history.getJSONObject(i);
                String when=android.text.format.DateFormat.format("dd/MM/yyyy HH:mm",entry.optLong("time")).toString();
                margin(content,entry(entry.optString("name","Juego"),entry.optString("status")+" · "+when,v->showHistory()),-2,10);
            }
        }catch(Exception e){margin(content,text("No se pudo cargar el historial.",14,MUTED,false),-2,16);}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        SharedPreferences saved=getSharedPreferences(BuildService.STATE,0);
        if(saved.getBoolean("busy",false)&&!BuildService.isRunning())saved.edit().putBoolean("busy",false).putString("log",saved.getString("log","")+"\\nLa conversión anterior se interrumpió.\\n").apply();
        LinearLayout root=column();root.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=column();content.setPadding(dp(20),dp(20),dp(20),dp(22));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=horizontal();nav.setPadding(dp(10),dp(2),dp(10),dp(4));nav.setBackground(shape(PANEL,22));root.addView(nav,new LinearLayout.LayoutParams(-1,dp(68)));
        setContentView(root);showPage(0);
    }
    @Override protected void onResume(){super.onResume();ui.post(poll);}
    @Override protected void onPause(){ui.removeCallbacks(poll);super.onPause();}
    private void pick(int request){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,request);}
    private void refresh(){
        if(fileDetails==null||convert==null||save==null)return;
        SharedPreferences state=getSharedPreferences(BuildService.STATE,0);
        boolean busy=copying||state.getBoolean("busy",false);
        String jar=getPreferences(0).getString("jar",""),jad=getPreferences(0).getString("jad",""),result=state.getString("result","");
        fileDetails.setText(jar.isEmpty()?"Seleccionar juego JAR":"▤  "+getPreferences(0).getString("name","Juego.jar")+(jad.isEmpty()?"":"  ·  JAD"));
        convert.setEnabled(!busy&&!jar.isEmpty());convert.setAlpha(convert.isEnabled()?1f:0.55f);
        save.setEnabled(!busy&&!result.isEmpty()&&new File(result).isFile());
        save.setVisibility(save.isEnabled()?View.VISIBLE:View.GONE);
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        status.setText(busy?"Convirtiendo juego…":save.isEnabled()?"APK lista para guardar":"");
        String lines=state.getString("log","");
        if(!lines.equals(previousLog)){log.setText(lines.isEmpty()?"":lines);previousLog=lines;}
        log.setVisibility(lines.isEmpty()?View.GONE:View.VISIBLE);
    }
    private void showProjects(){
        SharedPreferences prefs=getPreferences(0);
        String jar=prefs.getString("jar","");
        if(jar.isEmpty()){new android.app.AlertDialog.Builder(this).setTitle("Proyectos").setMessage("Selecciona un archivo JAR para consultar los metadatos del juego.").setPositiveButton("Cerrar",null).show();return;}
        files.execute(()->{
            String message;
            try {
                String jad=prefs.getString("jad","");
                GameJar game=new GameJar(new File(jar),jad.isEmpty()?null:new File(jad),prefs.getString("name","Juego.jar"));
                String vendor="No especificado";
                for(java.util.Map.Entry<String,String> entry:game.attributes.entrySet())if(entry.getKey().equalsIgnoreCase("MIDlet-Vendor"))vendor=entry.getValue();
                message="Juego: "+game.name+"\nProveedor: "+vendor+"\nVersión: "+game.version+"\nClase MIDlet: "+game.midlet+"\nID del paquete: "+game.appId+"\nResolución: "+game.width+" × "+game.height+" ("+game.confidence+")";
            }catch(Exception error){message="No se pudieron leer los metadatos: "+error.getMessage();}
            final String result=message;
            ui.post(()->new android.app.AlertDialog.Builder(this).setTitle("Proyecto · J2Droid").setMessage(result).setPositiveButton("Cerrar",null).show());
        });
    }
    private void showHistory(){
        SharedPreferences state=getSharedPreferences(BuildService.STATE,0);
        StringBuilder summary=new StringBuilder();
        try{
            org.json.JSONArray history=new org.json.JSONArray(state.getString("history","[]"));
            for(int i=history.length()-1;i>=0;i--){
                org.json.JSONObject entry=history.getJSONObject(i);
                String date=android.text.format.DateFormat.format("dd/MM/yyyy HH:mm",entry.optLong("time")).toString();
                summary.append(date).append(" · ").append(entry.optString("status")).append("\n").append(entry.optString("name")).append("\n\n");
            }
        }catch(Exception ignored){}
        new android.app.AlertDialog.Builder(this).setTitle("Historial de conversiones").setMessage(summary.length()==0?"Todavía no hay conversiones registradas.":summary.toString()).setPositiveButton("Cerrar",null).show();
    }
    private void startBuild() {
        SharedPreferences prefs=getPreferences(0);String jar=prefs.getString("jar",null),jad=prefs.getString("jad",null);
        if(jar==null)return;
        Intent intent=new Intent(this,BuildService.class).putExtra("jar",jar).putExtra("name",prefs.getString("name","Juego.jar"));if(jad!=null)intent.putExtra("jad",jad);
        SharedPreferences state=getSharedPreferences(BuildService.STATE,0);
        state.edit().putBoolean("busy",true).putString("result","").putString("log","").apply();refresh();
        try { startForegroundService(intent); }
        catch(RuntimeException error){state.edit().putBoolean("busy",false).putString("log","No se pudo iniciar la conversión: "+error).apply();refresh();}
    }
    private void askBackup(boolean restore){
        if(BuildService.isRunning()){Toast.makeText(this,"Espera a que termine la conversión",Toast.LENGTH_LONG).show();return;}
        EditText field=new EditText(this);field.setSingleLine(true);field.setHint("Contraseña de respaldo (mínimo 12 caracteres)");
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new android.app.AlertDialog.Builder(this).setTitle(restore?"Importar firma":"Exportar firma")
            .setMessage(restore?"Solo se permite restaurar antes de crear la primera firma. Necesitas la contraseña original.":"Guarda el archivo y la contraseña en un lugar seguro. Sin ambos, no podrás recuperar la firma.")
            .setView(field).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",(d,w)->{
                String password=field.getText().toString();
                if(password.length()<12){Toast.makeText(this,"Usa al menos 12 caracteres",Toast.LENGTH_LONG).show();return;}
                backupPassword=password;
                Intent intent=new Intent(restore?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE);
                if(!restore)intent.putExtra(Intent.EXTRA_TITLE,"J2Droid-firma.p12");
                startActivityForResult(intent,restore?5:4);
            }).show();
    }
    private void saveApk(){String result=getSharedPreferences(BuildService.STATE,0).getString("result","");if(result.isEmpty())return;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/vnd.android.package-archive").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,new File(result).getName());startActivityForResult(intent,3);}
    @Override protected void onActivityResult(int request,int code,Intent intent) {
        super.onActivityResult(request,code,intent);if(code!=RESULT_OK || intent==null || intent.getData()==null)return;
        Uri uri=intent.getData();copying=true;refresh();
        files.execute(()->{
            try {
                if(request==4 || request==5){
                    SigningProfile profile=new SigningProfile(this);
                    if(request==4){try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("No se pudo guardar");profile.exportTo(out,backupPassword);}}
                    else {try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("No se pudo leer");profile.importFrom(in,backupPassword);}}
                    ui.post(()->Toast.makeText(this,request==4?"Firma respaldada":"Firma restaurada",Toast.LENGTH_LONG).show());
                }else if(request==3) {
                    String result=getSharedPreferences(BuildService.STATE,0).getString("result","");
                    try(InputStream in=new FileInputStream(result);OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("No se pudo abrir el destino");OfflineBuilder.copy(in,out);}
                    new File(result).delete();getSharedPreferences(BuildService.STATE,0).edit().remove("result").apply();
                    ui.post(()->Toast.makeText(this,"APK guardada. Archivo temporal eliminado.",Toast.LENGTH_LONG).show());
                }else {
                    String name="Juego.jar";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
                    File target=new File(getFilesDir(),request==1?"input.jar":"input.jad"),temporary=new File(getFilesDir(),"input.tmp");
                    try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temporary)){if(in==null)throw new IOException("No se pudo leer el archivo");out.write(Archives.read(in,request==1?64*1024*1024:1024*1024));}
                    java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    SharedPreferences.Editor edit=getPreferences(0).edit().putString(request==1?"jar":"jad",target.getAbsolutePath());
                    if(request==1){edit.putString("name",name).remove("jad");getSharedPreferences(BuildService.STATE,0).edit().putString("result","").putString("log","").apply();}edit.apply();
                }
            }catch(Exception error){ui.post(()->Toast.makeText(this,error.toString(),Toast.LENGTH_LONG).show());}
            finally{backupPassword="";copying=false;ui.post(this::refresh);}
        });
    }
    @Override protected void onDestroy(){ui.removeCallbacks(poll);files.shutdown();super.onDestroy();}
}
