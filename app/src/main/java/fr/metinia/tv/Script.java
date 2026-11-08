package fr.metinia.tv;

/** Scripts injectés dans metin-ia.fr (seulement) par Metin TV. */
final class Script {
    private Script() {}

    /**
     * - SpeechRecognition / webkitSpeechRecognition : dictée par la reconnaissance vocale de la télé ;
     * - speechSynthesis / SpeechSynthesisUtterance : lecture à voix haute par la synthèse vocale de la télé ;
     * - case sélectionnée bien visible pour la navigation aux flèches ;
     * - __metinTVInserer : le texte dicté avec le bouton micro de la télécommande va dans la zone de message.
     */
    static final String TEXTE = """
        (function () {
          if (window.__metinTV) return;
          var N = window.MetinTVNatif;
          if (!N) return;
          window.__metinTV = true;
          var rappels = {}, n = 0;
          window.__metinTVRappel = function (id, type, texte) { var r = rappels[id]; if (r) r(type, texte); };

          function Reco() {
            this.lang = 'fr-FR'; this.interimResults = false; this.continuous = false; this.maxAlternatives = 1;
            this.onresult = null; this.onerror = null; this.onend = null; this.onstart = null; this._annule = false;
          }
          Reco.prototype.start = function () {
            var s = this, id = 'r' + (++n);
            s._annule = false;
            rappels[id] = function (type, texte) {
              if (type === 'resultat') {
                if (!s._annule && s.onresult) {
                  var res = [{ transcript: texte, confidence: 0.9 }];
                  res.isFinal = true;
                  s.onresult({ resultIndex: 0, results: [res] });
                }
              } else if (type === 'erreur') {
                if (s.onerror) s.onerror({ error: texte || 'network' });
              } else if (type === 'fin') {
                delete rappels[id];
                if (s.onend) s.onend({});
              }
            };
            N.ecouter(id, s.lang || 'fr-FR');
            if (s.onstart) s.onstart({});
          };
          Reco.prototype.stop = function () {};
          Reco.prototype.abort = function () { this._annule = true; };
          Reco.prototype.addEventListener = function (t, f) { this['on' + t] = f; };
          Reco.prototype.removeEventListener = function (t) { this['on' + t] = null; };
          window.SpeechRecognition = Reco;
          window.webkitSpeechRecognition = Reco;

          function Enonce(t) {
            this.text = t || ''; this.lang = 'fr-FR'; this.rate = 1; this.pitch = 1; this.volume = 1; this.voice = null;
            this.onend = null; this.onerror = null; this.onstart = null;
          }
          Enonce.prototype.addEventListener = function (t, f) { this['on' + t] = f; };
          Enonce.prototype.removeEventListener = function (t) { this['on' + t] = null; };
          var actuel = null;
          var synth = {
            speaking: false, pending: false, paused: false, onvoiceschanged: null,
            speak: function (u) {
              var id = 's' + (++n);
              actuel = id;
              synth.speaking = true;
              rappels[id] = function (type) {
                if (type === 'debut') { if (u.onstart) u.onstart({}); return; }
                delete rappels[id];
                if (actuel === id) { synth.speaking = false; actuel = null; }
                if (type === 'erreur') { if (u.onerror) u.onerror({ error: 'synthesis-failed' }); }
                else if (u.onend) u.onend({});
              };
              N.parler(id, String(u.text || ''), u.lang || 'fr-FR', +u.rate || 1, +u.pitch || 1);
            },
            cancel: function () { N.taire(); synth.speaking = false; actuel = null; },
            pause: function () {}, resume: function () {},
            getVoices: function () { return []; },
            addEventListener: function () {}, removeEventListener: function () {}
          };
          try { Object.defineProperty(window, 'speechSynthesis', { value: synth, configurable: true }); }
          catch (e) { window.speechSynthesis = synth; }
          window.SpeechSynthesisUtterance = Enonce;

          window.__metinTVInserer = function (t) {
            var e = document.activeElement;
            if (!e || !/^(TEXTAREA|INPUT)$/.test(e.tagName)) e = document.querySelector('textarea') || document.querySelector('input[type=text],input:not([type])');
            if (!e) return;
            var proto = e.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            var set = Object.getOwnPropertyDescriptor(proto, 'value').set;
            set.call(e, e.value ? e.value + ' ' + t : t);
            e.dispatchEvent(new Event('input', { bubbles: true }));
            e.focus();
          };

          function style() {
            if (document.getElementById('metin-tv-style')) return;
            var s = document.createElement('style');
            s.id = 'metin-tv-style';
            s.textContent = ':focus{outline:4px solid #7cf08a !important;outline-offset:3px !important}'
              + 'html{scroll-padding:80px}';
            (document.head || document.documentElement).appendChild(s);
          }
          if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', style); else style();
          document.addEventListener('focusin', function (e) {
            try { e.target.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (x) {}
          });
        })();
        """;

    static final String PAGE_HORS_LIGNE = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
        body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;
        background:#0a120c;color:#e6f5e8;font-family:monospace;gap:24px;text-align:center}
        h1{color:#7cf08a;font-size:48px;margin:0;letter-spacing:.1em}
        button{font:600 28px monospace;background:#7cf08a;color:#0a120c;border:0;border-radius:14px;padding:16px 32px}
        button:focus{outline:4px solid #e6f5e8;outline-offset:4px}
        </style></head><body>
        <h1>METIN</h1><p style="font-size:24px">Pas de connexion à Internet pour l'instant.</p>
        <button autofocus onclick="location.href='https://metin-ia.fr/?ecran=tele'">Réessayer</button>
        </body></html>
        """;
}
