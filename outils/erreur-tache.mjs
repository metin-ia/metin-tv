// Metin TV — version 1.0.0 (14 novembre 2026), copie de Metin/scripts/erreur-tache.mjs (dépôt metin-ia/Metin) :
// ici, seule la référence publique du projet est donnée (METIN_REFERENCE) ; aucune clé.
// Metin — une tâche GitHub en échec écrit sa propre erreur (page Admin › Erreurs).
// Lancé par le travail « erreurs » de chaque tâche (needs: tous les travaux, if: failure()), une fois les travaux finis :
// il lit les travaux en échec de CETTE exécution (jeton habituel, droit « actions: read »), l'étape en échec et les lignes
// juste avant « exit code » dans le journal, puis les envoie au serveur avec l'identité OIDC de la tâche (sans clé).
// Tout ce qui ressemble à une clé est masqué ici, et encore une fois par le serveur. Ne fait jamais échouer la tâche.
// Fichier autonome (seulement Node) : le même fichier sert dans le dépôt de Metin TV.
//   node erreur-tache.mjs            (variables : GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_ATTEMPT, GH_TOKEN,
//                                     ACTIONS_ID_TOKEN_REQUEST_URL/TOKEN, METIN_REFERENCE et METIN_CLE_PUBLIQUE ou hebergement.json)
import { existsSync, readFileSync } from "node:fs";

const env = process.env;
const fin = (m) => { console.log(`erreurs : ${m}`); process.exit(0); };

export function masquer(t) {
  return String(t)
    .replace(/\b(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|KGAT_[A-Za-z0-9_-]+|hf_[A-Za-z0-9]{20,}|sk-[A-Za-z0-9_-]{20,}|sb_(secret|publishable)_[A-Za-z0-9_-]+|AGE-SECRET-KEY-[A-Z0-9]+)\b/g, "***")
    .replace(/\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{5,}\b/g, "***")
    .replace(/([?&](token|sig|signature|key|apikey|access_token|X-Amz-[A-Za-z-]+)=)[^&\s"']+/gi, "$1***")
    .replace(/\b(authorization|apikey|x-api-key|x-github-oidc|cookie)\s*[:=]\s*\S+(\s+\S+)?/gi, "$1: ***");
}

/** les lignes utiles d'un journal de travail : celles juste avant « exit code » (sans l'heure ni les groupes) */
export function lignesAvantSortie(journal, n = 12) {
  const l = String(journal).split(/\r?\n/).map((x) => x.replace(/^\d{4}-\d\d-\d\dT[\d:.]+Z ?/, ""));
  let i = -1;
  for (let k = l.length - 1; k >= 0; k--) if (/##\[error\]Process completed with exit code|exit code \d+/.test(l[k])) { i = k; break; }
  const erreurs = l.filter((x) => /^##\[error\]/.test(x) && !/Process completed with exit code/.test(x)).map((x) => x.replace(/^##\[error\]/, "")).slice(-4);
  const avant = (i < 0 ? l.slice(-n) : l.slice(Math.max(0, i - n), i)).filter((x) => x.trim() && !/^##\[(group|endgroup)\]/.test(x));
  const tout = [...new Set([...avant, ...erreurs])];
  return masquer(tout.join("\n")).slice(-1800) || "(journal vide)";
}

async function principal() {
  const { GITHUB_REPOSITORY: depot, GITHUB_RUN_ID: run, GITHUB_RUN_ATTEMPT: essai = "1", GH_TOKEN: gh, ACTIONS_ID_TOKEN_REQUEST_URL: url, ACTIONS_ID_TOKEN_REQUEST_TOKEN: jeton } = env;
  if (!depot || !run || !gh || !url || !jeton) fin("variables de la tâche absentes (rien n'est envoyé)");
  let ref = env.METIN_REFERENCE, cle = env.METIN_CLE_PUBLIQUE;
  if (!ref || !cle) {
    for (const h of ["Metin/hebergement.json", "hebergement.json"]) if (existsSync(h)) { const j = JSON.parse(readFileSync(h, "utf8")); ref = j.supabase_reference; cle = j.supabase_cle_publique; break; }
  }
  if (!ref) fin("adresse du serveur inconnue");
  const api = async (chemin, texte = false) => {
    const r = await fetch(`https://api.github.com${chemin}`, { headers: { authorization: `Bearer ${gh}`, accept: "application/vnd.github+json" } });
    if (!r.ok) throw new Error(`GitHub ${r.status}`);
    return texte ? r.text() : r.json();
  };
  const { jobs = [] } = await api(`/repos/${depot}/actions/runs/${run}/attempts/${essai}/jobs?per_page=100`);
  const travaux = [];
  for (const j of jobs.filter((x) => x.conclusion === "failure" || x.conclusion === "timed_out").slice(0, 10)) {
    const etape = (j.steps ?? []).find((s) => s.conclusion === "failure")?.name ?? (j.conclusion === "timed_out" ? "temps dépassé" : "?");
    let message;
    try { message = lignesAvantSortie(await api(`/repos/${depot}/actions/jobs/${j.id}/logs`, true)); } catch (e) { message = `(journal illisible : ${e.message})`; }
    travaux.push({ travail: j.name, etape, message });
  }
  if (!travaux.length) fin("aucun travail en échec trouvé");
  const id = await fetch(`${url}&audience=${encodeURIComponent("metin-ia.fr/erreurs")}`, { headers: { Authorization: `Bearer ${jeton}` } });
  if (!id.ok) fin(`jeton d'identité : ${id.status}`);
  const r = await fetch(`https://${ref}.supabase.co/functions/v1/metin`, {
    method: "POST", headers: { ...(cle ? { apikey: cle } : {}), "x-github-oidc": (await id.json()).value, "content-type": "application/json" },
    body: JSON.stringify({ action: "erreurs.tache", travaux }),
  });
  fin(`${travaux.length} travail(aux) en échec envoyé(s) : ${r.status} ${(await r.text()).slice(0, 200)}`);
}
if (import.meta.url === `file://${process.argv[1]}`) principal().catch((e) => fin(`envoi impossible : ${e.message}`));
