package fr.metinia.tv;

/**
 * Le pointeur du mode souris (sans rien d'Android, essayé à part) : position, flèches tenues, accélération, bords.
 * - lent au début pour viser, puis de plus en plus vite tant que la flèche reste appuyée, jusqu'à traverser l'écran en
 *   une seconde environ ;
 * - au bord de l'écran, le pointeur s'arrête et la page défile dans ce sens.
 */
final class Souris {
    static final int HAUT = 0, BAS = 1, GAUCHE = 2, DROITE = 3;
    /** 1.0.3 : appui long sur Retour = changer de mode (OK gardé reste un vrai appui long sur la page) */
    static final long RETOUR_LONG_MS = 800;
    /** le pointeur se cache après 5 secondes sans bouger */
    static final long CACHE_APRES_MS = 5000;

    float x, y;
    int largeur, hauteur;
    boolean actif = false;
    private final boolean[] tenu = new boolean[4];
    private long debutTenu = 0;

    Souris(int largeur, int hauteur) {
        taille(largeur, hauteur);
        centrer();
    }

    void taille(int l, int h) {
        largeur = Math.max(1, l);
        hauteur = Math.max(1, h);
        x = Math.max(0, Math.min(largeur - 1, x));
        y = Math.max(0, Math.min(hauteur - 1, y));
    }

    void centrer() { x = largeur / 2f; y = hauteur / 2f; }

    void appuyer(int dir, long maintenant) {
        if (dir < 0 || dir > 3) return;
        if (aucune()) debutTenu = maintenant;
        tenu[dir] = true;
    }

    void relacher(int dir) {
        if (dir < 0 || dir > 3) return;
        tenu[dir] = false;
        if (aucune()) debutTenu = 0;
    }

    void toutRelacher() { for (int i = 0; i < 4; i++) tenu[i] = false; debutTenu = 0; }

    boolean aucune() { return !tenu[0] && !tenu[1] && !tenu[2] && !tenu[3]; }

    /** vitesse en pixels par image (60 images par seconde) selon le temps tenu */
    static float vitesse(long tenuMs, int largeur) {
        float min = Math.max(2f, largeur / 640f);
        float max = Math.max(min, largeur / 60f);  // l'écran entier en une seconde environ
        float t = Math.max(0, tenuMs - 150) / 900f;  // 150 ms de visée lente, puis 0,9 s pour atteindre la vitesse haute
        return Math.min(max, min + (max - min) * t * t);
    }

    /**
     * une image : le pointeur avance ; renvoie le défilement demandé au bord ({dx, dy} en -1, 0 ou 1), ou null si aucune
     * flèche n'est tenue
     */
    int[] pas(long maintenant) {
        if (aucune()) return null;
        float v = vitesse(maintenant - debutTenu, largeur);
        int dx = (tenu[DROITE] ? 1 : 0) - (tenu[GAUCHE] ? 1 : 0);
        int dy = (tenu[BAS] ? 1 : 0) - (tenu[HAUT] ? 1 : 0);
        float nx = x + dx * v, ny = y + dy * v;
        int[] bord = {0, 0};
        if (nx < 0) { nx = 0; bord[0] = -1; } else if (nx > largeur - 1) { nx = largeur - 1; bord[0] = 1; }
        if (ny < 0) { ny = 0; bord[1] = -1; } else if (ny > hauteur - 1) { ny = hauteur - 1; bord[1] = 1; }
        x = nx; y = ny;
        return bord;
    }
}
