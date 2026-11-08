package fr.metinia.tv;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
 * - le site reconnaît l'application par ?ecran=tele et par « MetinTV/1.0 » dans le User-Agent.
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

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vue = new WebView(this);
        vue.setBackgroundColor(Color.rgb(10, 18, 12));
        vue.setFocusable(true);
        vue.setFocusableInTouchMode(true);
        setContentView(vue);
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
        runOnUiThread(() -> vue.evaluateJavascript(js, null));
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
                vue.evaluateJavascript(js, null);
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

    // ------------------------------------------------------------------ touches de la télécommande
    @Override
    public boolean onKeyDown(int code, KeyEvent evenement) {
        if (code == KeyEvent.KEYCODE_BACK && vue.canGoBack()) {
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
    @Override protected void onResume() { super.onResume(); pleinEcran(); vue.onResume(); }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        vue.onPause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle etat) {
        super.onSaveInstanceState(etat);
        vue.saveState(etat);
    }

    @Override
    protected void onDestroy() {
        if (voix != null) { voix.stop(); voix.shutdown(); }
        vue.destroy();
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
