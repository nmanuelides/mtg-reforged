package io.github.nmanuelides.mtgreforged

import io.github.nmanuelides.mtgreforged.platform.ResId

/**
 * The Android app's bundled images and fonts (android/app/src/main/res), which the desktop
 * build copies onto the classpath. Same names as Android's generated R, so shared code
 * reads R.drawable.x / R.font.x on both platforms; here each one is its resource path.
 */
@Suppress("ClassName", "ObjectPropertyName")
object R {
    object drawable {
        const val art_ashiok: ResId = "drawable/art_ashiok.webp"
        const val art_calamity_titans: ResId = "drawable/art_calamity_titans.webp"
        const val art_deadeye_navigator: ResId = "drawable/art_deadeye_navigator.webp"
        const val art_dracula: ResId = "drawable/art_dracula.webp"
        const val art_dragoncaller: ResId = "drawable/art_dragoncaller.webp"
        const val art_elesh_norn: ResId = "drawable/art_elesh_norn.webp"
        const val art_hulking_raptor: ResId = "drawable/art_hulking_raptor.webp"
        const val art_kozilek: ResId = "drawable/art_kozilek.webp"
        const val art_moraug: ResId = "drawable/art_moraug.webp"
        const val art_nazgul: ResId = "drawable/art_nazgul.webp"
        const val art_rakdos: ResId = "drawable/art_rakdos.webp"
        const val art_serra: ResId = "drawable/art_serra.webp"
        const val art_sol_ring: ResId = "drawable/art_sol_ring.webp"
        const val art_tiamat: ResId = "drawable/art_tiamat.webp"
        const val foil_illusion: ResId = "drawable/foil_illusion.png"
        const val mercadopago_logo: ResId = "drawable/mercadopago_logo.png"
        const val mana_b: ResId = "drawable/mana_b.png"
        const val mana_c: ResId = "drawable/mana_c.png"
        const val mana_g: ResId = "drawable/mana_g.png"
        const val mana_r: ResId = "drawable/mana_r.png"
        const val mana_u: ResId = "drawable/mana_u.png"
        const val mana_w: ResId = "drawable/mana_w.png"
        const val paper_texture: ResId = "drawable/paper_texture.webp"
    }

    object font {
        const val cinzel: ResId = "font/cinzel.ttf"
        const val eb_garamond: ResId = "font/eb_garamond.ttf"
    }
}
