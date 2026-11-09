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
    private Button convert,save,install;
    private TextView selected,status,log,jadDetails;
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
    private int BG=0xff14111e, PANEL=0xff252032, ITEM=0xff342d43, WHITE=0xfff6f1ff, MUTED=0xffbcb0cf, LAVENDER=0xffcbb4ff;
    private boolean nightMode() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    private void applySystemPalette() {
        boolean dark=nightMode();
        if(Build.VERSION.SDK_INT>=31) {
            BG=getColor(dark?android.R.color.system_neutral1_900:android.R.color.system_neutral1_50);
            PANEL=getColor(dark?android.R.color.system_neutral1_800:android.R.color.system_neutral1_100);
            ITEM=getColor(dark?android.R.color.system_neutral2_700:android.R.color.system_neutral2_100);
            WHITE=getColor(dark?android.R.color.system_neutral1_50:android.R.color.system_neutral1_900);
            MUTED=getColor(dark?android.R.color.system_neutral2_200:android.R.color.system_neutral2_700);
            LAVENDER=getColor(dark?android.R.color.system_accent1_200:android.R.color.system_accent1_600);
        } else if(!dark) {
            BG=0xfff9f7fc;PANEL=0xffefebf5;ITEM=0xffe7e1ee;
            WHITE=0xff211b28;MUTED=0xff615b68;LAVENDER=0xff684a9d;
        }
    }
    private LinearLayout content;
    private TextView fileDetails;
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String text,int size,boolean bold){TextView v=text(text,size,WHITE,bold);return v;}
    private void margin(LinearLayout parent,View child,int height,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height<0?height:dp(height));p.topMargin=dp(top);parent.addView(child,p);}
    private LinearLayout entry(String title,String subtitle,View.OnClickListener action){
        LinearLayout l=column();l.setPadding(dp(17),dp(13),dp(17),dp(13));l.setBackground(shape(ITEM,17));
        l.addView(label(title,15,true));if(subtitle!=null)l.addView(text(subtitle,13,MUTED,false));l.setMinimumHeight(dp(62));l.setOnClickListener(action);return l;
    }
    private TextView action(String title,boolean primary,View.OnClickListener click){
        TextView v=text(title,primary?18:14,primary?BG:WHITE,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(shape(primary?LAVENDER:ITEM,primary?28:16));v.setMinimumHeight(dp(primary?58:48));v.setOnClickListener(click);return v;
    }
    private void section(String title){TextView v=text(title,17,LAVENDER,true);margin(content,v,-2,19);}
    private void showPage(int page) {
        content.removeAllViews();
        showConvertPage();
        refresh();
    }
    private void showConvertPage() {
        // The converter has one task. Keep only controls that directly support it.
        TextView title=label("J2Droid",30,true);
        margin(content,title,-2,7);
        try {
            String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            margin(content,text("Versión "+version,12,MUTED,false),-2,1);
        } catch(Exception ignored) {}
        margin(content,text("Convierte juegos Java ME a APK en tu teléfono.",14,MUTED,false),-2,2);

        section("Juego");
        fileDetails=text("Seleccionar archivo JAR",17,WHITE,true);
        LinearLayout jarCard=column();
        jarCard.setPadding(dp(20),dp(20),dp(20),dp(20));
        jarCard.setBackground(shape(PANEL,22));
        jarCard.addView(fileDetails);
        TextView jarHint=text("Toca aquí para elegir un archivo .jar",13,MUTED,false);
        jarCard.addView(jarHint);
        jarCard.setMinimumHeight(dp(94));
        jarCard.setOnClickListener(v->pick(1));
        margin(content,jarCard,-2,12);

        LinearLayout jadCard=horizontal();
        jadCard.setBackground(shape(ITEM,16));
        jadCard.setPadding(dp(14),dp(4),dp(8),dp(4));
        jadDetails=text("JAD opcional",14,WHITE,false);
        jadCard.addView(jadDetails,new LinearLayout.LayoutParams(0,-2,1));
        TextView jadButton=action("Elegir JAD",false,v->pick(2));
        jadCard.addView(jadButton);
        TextView removeJad=action("Quitar",false,v->{
            getPreferences(0).edit().remove("jad").apply();
            refresh();
        });
        removeJad.setVisibility(getPreferences(0).getString("jad","").isEmpty()?View.GONE:View.VISIBLE);
        jadCard.addView(removeJad);
        jadDetails.setTag(removeJad);
        margin(content,jadCard,-2,10);

        convert=new Button(this);
        convert.setText("Crear APK");
        convert.setTextSize(17);
        convert.setAllCaps(false);
        convert.setTextColor(BG);
        convert.setBackground(shape(LAVENDER,22));
        convert.setOnClickListener(v->startBuild());
        margin(content,convert,60,22);

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        margin(content,progress,4,18);
        status=text("",14,MUTED,false);
        margin(content,status,-2,9);

        LinearLayout actions=horizontal();
        install=new Button(this);
        install.setText("Instalar APK");
        install.setAllCaps(false);
        install.setTextColor(BG);
        install.setBackground(shape(LAVENDER,17));
        install.setOnClickListener(v->installApk());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(52),1);
        ap.rightMargin=dp(6);
        actions.addView(install,ap);
        save=new Button(this);
        save.setText("Guardar APK");
        save.setAllCaps(false);
        save.setTextColor(WHITE);
        save.setBackground(shape(ITEM,17));
        save.setOnClickListener(v->saveApk());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(52),1);
        sp.leftMargin=dp(6);
        actions.addView(save,sp);
        margin(content,actions,-2,7);

        log=text("",12,MUTED,false);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextIsSelectable(true);
        margin(content,log,-2,14);

        margin(content,entry("Firma de los juegos","Respaldar o restaurar la firma para conservar actualizaciones",v->showSigningOptions()),-2,28);
        margin(content,text("Las conversiones se realizan sin conexión.",12,MUTED,false),-2,14);
    }
    private void showSigningOptions() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Firma de juegos")
            .setMessage("Conserva tu firma para poder actualizar los juegos que ya convertiste. Respáldala antes de borrar los datos de J2Droid.")
            .setPositiveButton("Respaldar",(d,w)->askBackup(false))
            .setNeutralButton("Restaurar",(d,w)->askBackup(true))
            .setNegativeButton("Cancelar",null).show();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        applySystemPalette();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(nightMode()?0:(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR));
        SharedPreferences saved=getSharedPreferences(BuildService.STATE,0);
        if(saved.getBoolean("busy",false)&&!BuildService.isRunning())saved.edit().putBoolean("busy",false).putString("log",saved.getString("log","")+"\\nLa conversión anterior se interrumpió.\\n").apply();
        LinearLayout root=column();root.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=column();content.setPadding(dp(20),dp(20),dp(20),dp(22));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
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
        fileDetails.setText(jar.isEmpty()?"Seleccionar archivo JAR":getPreferences(0).getString("name","Juego.jar"));
        if(jadDetails!=null) {
            jadDetails.setText(jad.isEmpty()?"JAD opcional":"Archivo JAD seleccionado");
            View remove=(View)jadDetails.getTag();
            if(remove!=null)remove.setVisibility(jad.isEmpty()?View.GONE:View.VISIBLE);
        }
        convert.setEnabled(!busy&&!jar.isEmpty());convert.setAlpha(convert.isEnabled()?1f:0.55f);
        save.setEnabled(!busy&&!result.isEmpty()&&new File(result).isFile());
        save.setVisibility(save.isEnabled()?View.VISIBLE:View.GONE);
        install.setEnabled(save.isEnabled());install.setVisibility(save.isEnabled()?View.VISIBLE:View.GONE);
        progress.setVisibility(busy?View.VISIBLE:View.GONE);
        status.setText(busy?"Convirtiendo juego…":save.isEnabled()?"APK lista para instalar o guardar":"");
        String lines=state.getString("log","");
        if(!lines.equals(previousLog)){log.setText(lines.isEmpty()?"":lines);previousLog=lines;}
        log.setVisibility(lines.isEmpty()?View.GONE:View.VISIBLE);
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
    private void installApk(){
        String path=getSharedPreferences(BuildService.STATE,0).getString("result","");
        File apk=new File(path);
        if(!apk.isFile()){Toast.makeText(this,"Primero crea una APK",Toast.LENGTH_LONG).show();return;}
        if(android.os.Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
            new android.app.AlertDialog.Builder(this).setTitle("Permitir instalación")
                .setMessage("Android requiere autorizar a J2Droid para instalar las APK que genera. No es necesario guardarlas en Descargas.")
                .setNegativeButton("Cancelar",null).setPositiveButton("Abrir ajustes",(d,w)->{
                    Intent settings=new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+getPackageName()));startActivity(settings);
                }).show();
            return;
        }
        try{
            Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);
            Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(view);
        }catch(Exception e){new android.app.AlertDialog.Builder(this).setTitle("No se pudo instalar")
            .setMessage(e.getMessage()).setPositiveButton("Cerrar",null).show();}
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
