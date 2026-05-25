package com.example.data

data class ScriptureVerse(
    val reference: String,
    val text: String,
    val context: String
)

data class ClassicPrayer(
    val title: String,
    val text: String,
    val category: String
)

data class LectioDivinaTemplate(
    val title: String,
    val scripture: String,
    val lectioText: String,
    val meditatioText: String,
    val oratioText: String,
    val contemplatioText: String
)

object CatholicContent {
    val dailyVerses = listOf(
        ScriptureVerse(
            reference = "Lucas 1:38",
            text = "Entonces María dijo: He aquí la sierva del Señor; hágase en mí según tu palabra. Y el ángel se retiró de ella.",
            context = "La Anunciación. Un recordatorio de la entrega total a la voluntad divina, el 'FIAT' que cambió la historia del mundo."
        ),
        ScriptureVerse(
            reference = "Mateo 11:28",
            text = "Venid a mí todos los que estáis trabajados y cargados, y yo os haré descansar.",
            context = "Jesús nos invita a entregar nuestras cargas. En la comunión con Él, el alma fatigada encuentra verdadero consuelo y descanso."
        ),
        ScriptureVerse(
            reference = "Filipenses 4:6-7",
            text = "Por nada estéis afanosos, sino sean conocidas vuestras peticiones delante de Dios en toda oración y ruego, con acción de gracias. Y la paz de Dios, que sobrepasa todo entendimiento, guardará vuestros corazones y vuestros pensamientos en Cristo Jesús.",
            context = "San Pablo nos enseña a trocar la angustia humana por la súplica agradecida. La paz divina custodia la mente y el corazón."
        ),
        ScriptureVerse(
            reference = "Salmo 23:1-3",
            text = "Jehová es mi pastor; nada me faltará. En lugares de delicados pastos me hará descansar; junto a aguas de reposo me pastoreará. Confortará mi alma.",
            context = "El Pastor Divino. Una de las expresiones más conmovedoras de confianza absoluta y seguridad en la divina providencia."
        ),
        ScriptureVerse(
            reference = "Juan 14:27",
            text = "La paz os dejo, mi paz os doy; yo no os la doy como el mundo la da. No se turbe vuestro corazón, ni tenga miedo.",
            context = "Jesús otorga una paz espiritual interior, imperturbable ante los vaivenes políticos, económicos o tribulaciones terrenales."
        )
    )

    val classicPrayers = listOf(
        ClassicPrayer(
            title = "Padre Nuestro",
            text = "Padre nuestro, que estás en el cielo,\nsantificado sea tu Nombre;\nvenga a nosotros tu reino;\nhágase tu voluntad en la tierra como en el cielo.\nDanos hoy nuestro pan de cada día;\nperdona nuestras ofensas,\ncomo también nosotros perdonamos a los que nos ofenden;\nno nos dejes caer en la tentación,\ny líbranos del mal. Amén.",
            category = "Clásicas"
        ),
        ClassicPrayer(
            title = "Ave María",
            text = "Dios te salve, María, llena eres de gracia,\nel Señor es contigo;\nbendita tú eres entre todas las mujeres,\ny bendito es el fruto de tu vientre, Jesús.\nSanta María, Madre de Dios,\nruega por nosotros, pecadores,\nahora y en la hora de nuestra muerte. Amén.",
            category = "Clásicas"
        ),
        ClassicPrayer(
            title = "Oración por la Paz (San Francisco)",
            text = "Señor, hazme un instrumento de tu paz.\nDonde haya odio, siembre yo amor;\ndonde haya injuria, perdón;\ndonde haya duda, fe;\ndonde haya desesperación, esperanza;\ndonde haya tinieblas, luz;\ndonde haya tristeza, alegría.\n\nOh Divino Maestro, concédeme que no busque ser consolado como consolar,\nser comprendido como comprender,\nser amado como amar.\nPorque dando es como recibimos,\nperdonando es como somos perdonados,\ny muriendo en ti es como nacemos a la vida eterna. Amén.",
            category = "Paz"
        ),
        ClassicPrayer(
            title = "Anima Christi (Alma de Cristo)",
            text = "Alma de Cristo, santifícame.\nCuerpo de Cristo, sálvame.\nSangre de Cristo, embriágame.\nAgua del costado de Cristo, lávame.\nPasión de Cristo, confórtame.\nOh, buen Jesús, óyeme.\nDentro de tus llagas, escóndeme.\nNo permitas que me aparte de Ti.\nDel enemigo maligno, defiéndeme.\nEn la hora de mi muerte, llámame.\nY mándame ir a Ti,\npara que con tus santos te alabe\npor los siglos de los siglos. Amén.",
            category = "Fortaleza"
        ),
        ClassicPrayer(
            title = "Oración de Confianza (San Ignacio)",
            text = "Tomad, Señor, y recibid\ntoda mi libertad, mi memoria,\nmi entendimiento y toda mi voluntad,\ntodo mi haber y mi poseer.\nVos me lo disteis, a Vos, Señor, lo torno.\nTodo es vuestro, disponed de ello\na toda vuestra voluntad.\nDadme vuestro amor y gracia, que esto me basta. Amén.",
            category = "Gratitud"
        )
    )

    val lectioDivinaTemplates = listOf(
        LectioDivinaTemplate(
            title = "Lectio Divina: El Buen Pastor",
            scripture = "Juan 10:11-14\n\"Yo soy el buen pastor; el buen pastor su vida da por las ovejas...\"",
            lectioText = "Lee atentamente el pasaje de Juan 10. Deja que las palabras ingresen a tu mente. Observa cómo Jesús se identifica a sí mismo como el Pastor que no huye ante las tribulaciones o el peligro, sino que entrega su propia vida por el rebaño, a diferencia del asalariado.",
            meditatioText = "¿Qué me dice Dios hoy en este pasaje? Reflexiona si te consideras parte del rebaño que escucha, reconoce y sigue Su voz de forma activa. Deja que Su llamado personal te hable en las circunstancias actuales de tu vida.",
            oratioText = "Háblale a Dios directamente. Pídele perdón por las ocasiones en las que has seguido voces extrañas que te alejaron de la fe. Agradécele por buscarte incansablemente cuando estabas perdido y por su infinito amor.",
            contemplatioText = "Reposa en Su presencia divina. Descansa bajo la custodia del Buen Pastor. Visualízate entre sus brazos, seguro y en paz profunda. Permanece en silencio adorándole."
        ),
        LectioDivinaTemplate(
            title = "Lectio Divina: Confianza en las tormentas",
            scripture = "Mateo 8:23-27\n\"Y de repente se levantó una gran tempestad... Mas él dormía...\"",
            lectioText = "Lee el relato de la tormenta en el mar de Galilea. Visualiza la barca, el oleaje que la cubre y el asombroso contraste entre el pánico de los discípulos y la absoluta quietud y paz del Señor que duerme.",
            meditatioText = "Reflexiona sobre las tormentas actuales en tu vida (ansiedades, conflictos familiares, dudas de fe o salud). ¿Por qué tenemos miedo? ¿Está nuestro corazón anclado en la certeza del poder redentor e intercesor de Cristo?",
            oratioText = "Clama al Señor desde lo hondo de tu corazón: '¡Sálvanos, Señor, que perecemos!'. Suplica que sople una mansa calma sobre tus tempestades interiores e incrementa nuestra fe debilitada.",
            contemplatioText = "Quédate en el gran sosiego que deja Jesús tras reprender al viento y al mar. Siente cómo cesan las turbulaciones y deléitate en ese silencio sagrado que solo Dios puede obrar."
        )
    )
}
