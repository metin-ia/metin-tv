package fr.metinia.tv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Metin TV : ouvre metin-ia.fr en plein écran sur Google TV.
 * - navigation aux flèches de la télécommande (case sélectionnée bien visible), Retour = page précédente ;
 * - dictée : la reconnaissance vocale de la télé remplace SpeechRecognition (absente des WebView) ;
 * - lecture à voix haute : la synthèse vocale de la télé remplace speechSynthesis (absente des WebView) ;
 * - le site reconnaît l'application par ?ecran=tele et par « MetinTV/1.0 » dans le User-Agent ;
 * - mode souris (1.0.3) : appui long sur OK pour basculer ; une flèche déplacée par les flèches de la télécommande
 *   (accélère si on garde appuyé), OK = toucher à cet endroit, OK gardé + flèches = glisser (tourner le Cerveau 3D),
 *   défilement au bord ; mode normal à chaque lancement ;
 * - si le moteur de la page tombe, la page est rechargée au lieu de rester figée.
 * Aucune clé, aucune donnée : la connexion reste gardée par le site lui-même (stockage du WebView).
 */
public class Principale extends Activity {

    private static final String ADRESSE = "https://metin-ia.fr/?ecran=tele";
    private static final String HOTE = "metin-ia.fr";
    private static final int DEMANDE_DICTEE = 41;
    private static final int DEMANDE_MICRO = 42;

    private WebView vue;
    private TextToSpeech voix;
    private boolean voixPrete = false;
    private volatile boolean hoteAutorise = false;
    private String dicteeEnCours = null;
    private PermissionRequest permissionEnAttente = null;
    private String enonceEnCours = null;

    // ------------------------------------------------------------------ mode souris
    private FrameLayout racine;
    private Fleche fleche;
    private TextView message;
    private Souris souris;
    private final Handler boucle = new Handler(Looper.getMainLooper());
    private boolean okBas = false, okConsomme = false, glisse = false, toucheEnCours = false, boucleActive = false;
    private long debutToucher = 0, dernierGeste = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vue = new WebView(this);
        vue.setBackgroundColor(Color.rgb(10, 18, 12));
        vue.setFocusable(true);
        vue.setFocusableInTouchMode(true);
        racine = new FrameLayout(this);
        racine.addView(vue, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        souris = new Souris(1920, 1080);
        fleche = new Fleche(this);
        fleche.setVisibility(View.INVISIBLE);
        racine.addView(fleche, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        message = new TextView(this);
        message.setTextColor(Color.rgb(10, 18, 12));
        message.setBackgroundColor(Color.rgb(124, 240, 138));
        message.setTextSize(22);
        int m = (int) (14 * getResources().getDisplayMetrics().density);
        message.setPadding(m * 2, m, m * 2, m);
        message.setVisibility(View.GONE);
        FrameLayout.LayoutParams pm = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pm.topMargin = m * 2;
        racine.addView(message, pm);
        racine.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> souris.taille(r - l, b - t));
        setContentView(racine);
        pleinEcran();

        WebSettings r = vue.getSettings();
        r.setJavaScriptEnabled(true);
        r.setDomStorageEnabled(true);
        r.setDatabaseEnabled(true);
        r.setMediaPlaybackRequiresUserGesture(false);
        r.setUseWideViewPort(true);
        r.setLoadWithOverviewMode(false);
        r.setTextZoom(115);
        r.setSupportZoom(false);
        r.setAllowFileAccess(false);
        r.setAllowContentAccess(false);
        r.setUserAgentString(r.getUserAgentString() + " MetinTV/1.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(vue, true);

        vue.addJavascriptInterface(new Pont(), "MetinTVNatif");
        boolean scriptAuDebut = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (scriptAuDebut) {
            WebViewCompat.addDocumentStartJavaScript(vue, Script.TEXTE, Collections.singleton("https://" + HOTE));
        }

        vue.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icone) {
                hoteAutorise = estMetin(url);
                if (!scriptAuDebut && hoteAutorise) v.evaluateJavascript(Script.TEXTE, null);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                hoteAutorise = estMetin(url);
                if (hoteAutorise) v.evaluateJavascript(Script.TEXTE, null);
                CookieManager.getInstance().flush();
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean recharge) {
                hoteAutorise = estMetin(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest demande) {
                String s = demande.getUrl().getScheme();
                return !("https".equals(s) || "http".equals(s));
            }

            /** le moteur de la page est tombé (mémoire de la télé) : on recharge au lieu de rester figé */
            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                if (v == vue) {
                    racine.removeView(vue);
                    vue.destroy();
                    vue = null;
                    recreate();
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest demande, WebResourceError erreur) {
                if (demande.isForMainFrame()) {
                    v.loadDataWithBaseURL(null, Script.PAGE_HORS_LIGNE, "text/html", "utf-8", null);
                }
            }
        });

        vue.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest demande) {
                runOnUiThread(() -> {
                    boolean micro = false;
                    for (String res : demande.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) micro = true;
                    }
                    if (!micro || !estMetin(demande.getOrigin().toString())) { demande.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        demande.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        permissionEnAttente = demande;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, DEMANDE_MICRO);
                    }
                });
            }
        });

        voix = new TextToSpeech(this, statut -> {
            voixPrete = statut == TextToSpeech.SUCCESS;
            if (voixPrete) voix.setLanguage(Locale.FRANCE);
        });
        voix.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { rappel(id, "debut", ""); }
            @Override public void onDone(String id) { rappel(id, "fin", ""); }
            @Override public void onError(String id) { rappel(id, "erreur", ""); }
            @Override public void onStop(String id, boolean interrompu) { rappel(id, "fin", ""); }
        });

        if (etat != null) vue.restoreState(etat);
        else vue.loadUrl(ADRESSE);
    }

    private static boolean estMetin(String url) {
        if (url == null) return false;
        String h = Uri.parse(url).getHost();
        return h != null && (h.equals(HOTE) || h.endsWith("." + HOTE));
    }

    private void pleinEcran() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /** renvoie un résultat au script de la page */
    private void rappel(String id, String type, String texte) {
        if (id == null) return;
        String js = "window.__metinTVRappel&&window.__metinTVRappel(" + JSONObject.quote(id) + ","
                + JSONObject.quote(type) + "," + JSONObject.quote(texte == null ? "" : texte) + ")";
        runOnUiThread(() -> { if (vue != null) vue.evaluateJavascript(js, null); });
    }

    // ------------------------------------------------------------------ dictée
    private void ecouter(String id, String langue) {
        if (dicteeEnCours != null) rappel(dicteeEnCours, "fin", "");
        dicteeEnCours = id;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langue == null || langue.isEmpty() ? "fr-FR" : langue);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez à Metin");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(i, DEMANDE_DICTEE);
        } catch (ActivityNotFoundException e) {
            rappel(id, "erreur", "service-not-allowed");
            rappel(id, "fin", "");
            dicteeEnCours = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int resultat, Intent donnees) {
        super.onActivityResult(code, resultat, donnees);
        if (code != DEMANDE_DICTEE) return;
        String id = dicteeEnCours;
        dicteeEnCours = null;
        String texte = "";
        if (resultat == RESULT_OK && donnees != null) {
            ArrayList<String> liste = donnees.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (liste != null && !liste.isEmpty()) texte = liste.get(0);
        }
        if ("telecommande".equals(id)) {
            if (!texte.isEmpty()) {
                String js = "window.__metinTVInserer&&window.__metinTVInserer(" + JSONObject.quote(texte) + ")";
                if (vue != null) vue.evaluateJavascript(js, null);
            }
            return;
        }
        if (!texte.isEmpty()) rappel(id, "resultat", texte);
        rappel(id, "fin", "");
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] resultats) {
        super.onRequestPermissionsResult(code, permissions, resultats);
        if (code != DEMANDE_MICRO || permissionEnAttente == null) return;
        boolean ok = resultats.length > 0 && resultats[0] == PackageManager.PERMISSION_GRANTED;
        if (ok) permissionEnAttente.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        else permissionEnAttente.deny();
        permissionEnAttente = null;
    }

    // ------------------------------------------------------------------ mode souris

    private static int direction(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return Souris.HAUT;
            case KeyEvent.KEYCODE_DPAD_DOWN: return Souris.BAS;
            case KeyEvent.KEYCODE_DPAD_LEFT: return Souris.GAUCHE;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Souris.DROITE;
            default: return -1;
        }
    }

    private static boolean estOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (vue == null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        if (estOk(code)) return toucheOk(e);
        int dir = direction(code);
        if (souris.actif && dir >= 0) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) souris.appuyer(dir, SystemClock.uptimeMillis());
                if (okBas) glisse = true;
                montrerFleche();
                demarrerBoucle();
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                souris.relacher(dir);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** OK : bref = clic (mode normal) ou toucher (mode souris) ; long = changer de mode ; gardé + flèches = glisser */
    private boolean toucheOk(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() > 0) return true;
            okBas = true; okConsomme = false; glisse = false;
            if (souris.actif) { montrerFleche(); toucher(MotionEvent.ACTION_DOWN); }
            boucle.postDelayed(appuiLong, Souris.APPUI_LONG_MS);
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) {
            boucle.removeCallbacks(appuiLong);
            okBas = false;
            if (okConsomme) return true;
            if (souris.actif) toucher(MotionEvent.ACTION_UP);
            else {
                // mode normal : le clic habituel, transmis à la page
                vue.dispatchKeyEvent(new KeyEvent(e.getDownTime(), e.getDownTime(), KeyEvent.ACTION_DOWN, e.getKeyCode(), 0));
                vue.dispatchKeyEvent(new KeyEvent(e.getDownTime(), e.getEventTime(), KeyEvent.ACTION_UP, e.getKeyCode(), 0));
            }
            return true;
        }
        return true;
    }

    private final Runnable appuiLong = () -> {
        if (!okBas || glisse) return;
        okConsomme = true;
        if (souris.actif && toucheEnCours) toucher(MotionEvent.ACTION_CANCEL);
        basculer();
    };

    private void basculer() {
        souris.actif = !souris.actif;
        souris.toutRelacher();
        if (souris.actif) { souris.centrer(); montrerFleche(); dire("Mode souris"); }
        else { fleche.setVisibility(View.INVISIBLE); dire("Mode normal"); }
    }

    private void dire(String texte) {
        message.setText(texte);
        message.setVisibility(View.VISIBLE);
        boucle.removeCallbacks(cacherMessage);
        boucle.postDelayed(cacherMessage, 2000);
    }

    private final Runnable cacherMessage = () -> message.setVisibility(View.GONE);

    private void montrerFleche() {
        dernierGeste = SystemClock.uptimeMillis();
        fleche.setVisibility(View.VISIBLE);
        fleche.invalidate();
        boucle.removeCallbacks(cacherFleche);
        boucle.postDelayed(cacherFleche, Souris.CACHE_APRES_MS);
    }

    private final Runnable cacherFleche = () -> {
        if (souris.actif && !okBas && souris.aucune() && SystemClock.uptimeMillis() - dernierGeste >= Souris.CACHE_APRES_MS) fleche.setVisibility(View.INVISIBLE);
    };

    /** un vrai toucher du doigt à l'endroit de la flèche : la page le reçoit comme sur un écran tactile */
    private void toucher(int action) {
        if (vue == null) return;
        long t = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) { debutToucher = t; toucheEnCours = true; }
        else if (!toucheEnCours) return;
        MotionEvent ev = MotionEvent.obtain(debutToucher, t, action, souris.x, souris.y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        vue.dispatchTouchEvent(ev);
        ev.recycle();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) toucheEnCours = false;
    }

    /** au bord : la page défile sous la flèche, comme avec la molette d'une souris */
    private void defiler(int dx, int dy) {
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0; pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = souris.x; pc[0].y = souris.y;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, -dy);
        pc[0].setAxisValue(MotionEvent.AXIS_HSCROLL, dx);
        long t = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, pp, pc, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        vue.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    private void demarrerBoucle() {
        if (boucleActive) return;
        boucleActive = true;
        boucle.post(image);
    }

    private int imagesAuBord = 0;
    private final Runnable image = new Runnable() {
        @Override public void run() {
            if (vue == null || !souris.actif) { boucleActive = false; return; }
            int[] bord = souris.pas(SystemClock.uptimeMillis());
            if (bord == null) { boucleActive = false; imagesAuBord = 0; return; }
            dernierGeste = SystemClock.uptimeMillis();
            fleche.invalidate();
            if (toucheEnCours) toucher(MotionEvent.ACTION_MOVE);
            else if ((bord[0] != 0 || bord[1] != 0) && ++imagesAuBord % 6 == 0) defiler(bord[0], bord[1]);
            boucle.postDelayed(this, 16);
        }
    };

    /** la flèche, dessinée par-dessus la page (elle ne prend jamais les touchers) */
    private final class Fleche extends View {
        private final Paint plein = new Paint(Paint.ANTI_ALIAS_FLAG), bord = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path forme = new Path();

        Fleche(Context c) {
            super(c);
            setFocusable(false);
            setClickable(false);
            float d = c.getResources().getDisplayMetrics().density * 1.4f;
            forme.moveTo(0, 0);
            forme.lineTo(0, 24 * d);
            forme.lineTo(6.5f * d, 18.5f * d);
            forme.lineTo(11 * d, 28 * d);
            forme.lineTo(15 * d, 26.2f * d);
            forme.lineTo(10.6f * d, 17 * d);
            forme.lineTo(19 * d, 17 * d);
            forme.close();
            plein.setColor(Color.WHITE);
            plein.setStyle(Paint.Style.FILL);
            bord.setColor(Color.rgb(10, 18, 12));
            bord.setStyle(Paint.Style.STROKE);
            bord.setStrokeWidth(2.2f * d);
            bord.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            if (!souris.actif) return;
            canvas.save();
            canvas.translate(souris.x, souris.y);
            canvas.drawPath(forme, bord);
            canvas.drawPath(forme, plein);
            canvas.restore();
        }
    }

    // ------------------------------------------------------------------ touches de la télécommande
    @Override
    public boolean onKeyDown(int code, KeyEvent evenement) {
        if (code == KeyEvent.KEYCODE_BACK && vue != null && vue.canGoBack()) {
            vue.goBack();
            return true;
        }
        if (code == KeyEvent.KEYCODE_SEARCH || code == KeyEvent.KEYCODE_VOICE_ASSIST) {
            ecouter("telecommande", "fr-FR");
            return true;
        }
        return super.onKeyDown(code, evenement);
    }

    @Override
    public boolean onSearchRequested() {
        ecouter("telecommande", "fr-FR");
        return true;
    }

    // ------------------------------------------------------------------ cycle de vie
    @Override protected void onResume() { super.onResume(); pleinEcran(); if (vue != null) vue.onResume(); }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        if (vue != null) vue.onPause();
        // mode souris : rien ne reste appuyé quand on quitte
        if (souris != null) { souris.toutRelacher(); if (toucheEnCours) toucher(MotionEvent.ACTION_CANCEL); }
        okBas = false;
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle etat) {
        super.onSaveInstanceState(etat);
        if (vue != null) vue.saveState(etat);
    }

    @Override
    protected void onDestroy() {
        boucle.removeCallbacksAndMessages(null);
        if (voix != null) { voix.stop(); voix.shutdown(); }
        if (vue != null) vue.destroy();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ pont avec la page (metin-ia.fr seulement)
    private class Pont {
        @JavascriptInterface
        public void ecouter(String id, String langue) {
            if (!hoteAutorise) return;
            runOnUiThread(() -> Principale.this.ecouter(id, langue));
        }

        @JavascriptInterface
        public void parler(String id, String texte, String langue, double vitesse, double hauteur) {
            if (!hoteAutorise) return;
            if (!voixPrete) { rappel(id, "erreur", ""); return; }
            enonceEnCours = id;
            Locale l = Locale.forLanguageTag(langue == null || langue.isEmpty() ? "fr-FR" : langue);
            if (voix.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE) voix.setLanguage(l);
            voix.setSpeechRate((float) Math.max(0.3, Math.min(3.0, vitesse)));
            voix.setPitch((float) Math.max(0.5, Math.min(2.0, hauteur)));
            voix.speak(texte == null ? "" : texte, TextToSpeech.QUEUE_FLUSH, null, id);
        }

        @JavascriptInterface
        public void taire() {
            if (!hoteAutorise) return;
            if (voix != null) voix.stop();
        }

        @JavascriptInterface
        public String version() { return "1.0"; }
    }
}
