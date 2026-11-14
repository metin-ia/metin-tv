package fr.metinia.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** le pointeur du mode souris : départ au centre, visée lente, accélération, bords, défilement */
public class SourisTest {
    @Test public void departAuCentreSansMouvement() {
        Souris s = new Souris(1920, 1080);
        assertEquals(960f, s.x, 0.01f);
        assertEquals(540f, s.y, 0.01f);
        assertNull(s.pas(1000));
    }

    @Test public void lentPourViserPuisRapide() {
        float debut = Souris.vitesse(0, 1920), milieu = Souris.vitesse(600, 1920), fin = Souris.vitesse(5000, 1920);
        assertTrue("lent au début (" + debut + ")", debut <= 4f);
        assertTrue("accélère", milieu > debut && fin > milieu);
        assertEquals("traverse l'écran en une seconde environ", 32f, fin, 0.01f);
    }

    @Test public void traverseLEcranEnMoinsDeDeuxSecondes() {
        Souris s = new Souris(1920, 1080);
        s.x = 0;
        s.appuyer(Souris.DROITE, 0);
        int images = 0;
        while (s.x < 1919 && images < 600) { s.pas(images * 16L); images++; }
        assertTrue("images : " + images, images * 16 < 2000);
    }

    @Test public void auBordIlSArreteEtLaPageDefile() {
        Souris s = new Souris(100, 100);
        s.y = 1;
        s.appuyer(Souris.HAUT, 0);
        int[] bord = s.pas(2000);
        assertEquals(0f, s.y, 0.01f);
        assertArrayEquals(new int[]{0, -1}, bord);
        s.relacher(Souris.HAUT);
        assertNull(s.pas(2100));
    }

    @Test public void diagonaleEtRelache() {
        Souris s = new Souris(1000, 1000);
        s.appuyer(Souris.BAS, 0);
        s.appuyer(Souris.DROITE, 0);
        s.pas(100);
        assertTrue(s.x > 500 && s.y > 500);
        s.toutRelacher();
        assertTrue(s.aucune());
    }
}
