package com.example.data

object StaticTvChannels {
    private val DEPORTES_LIST = listOf(
        "TUDN" to "tudn.html",
        "TNT SPORTS" to "tntsports.html",
        "ESPN PREMIUM LAT" to "espnpremium.html",
        "ESPN PREMIUM ARGENTINA" to "espnpremiumargentina.html",
        "TYC SPORTS" to "tycsports.html",
        "FOX SPORTS" to "foxsports.html",
        "FOX SPORTS 2" to "foxsports2.html",
        "FOX SPORTS 3" to "foxsports3.html",
        "FOX DEPORTES" to "foxdeportes.html",
        "ESPN LAT" to "espn.html",
        "ESPN ARGENTINA" to "espnar.html",
        "ESPN COLOMBIA" to "espncol.html",
        "ESPN 2" to "espn2.html",
        "ESPN 3" to "espn3.html",
        "ESPN 4" to "espn4.html",
        "ESPN 5" to "espn5.html",
        "ESPN 6" to "espn6.html",
        "ESPN 7" to "espn7.html",
        "DIRECTV SPORTS" to "directvsports.html",
        "DIRECTV SPORTS 2" to "directvsports2.html",
        "DIRECTV SPORTS PLUS" to "directvsportsplus.html",
        "FOX SPORTS MX" to "foxsportsmexico.html",
        "FOX SPORTS 2 MX" to "foxsports2mexico.html",
        "FOX SPORTS 3 MX" to "foxsports3mexico.html",
        "ESPN MX" to "espnmexico.html",
        "ESPN 2 MX" to "espn2mexico.html",
        "ESPN 3 MX" to "espn3mexico.html",
        "ESPN 4 MX" to "espn4mexico.html",
        "ESPN 5 MX" to "espn5mexico.html",
        "TVC DEPORTES" to "tvcdeportes.html",
        "LIGA 1" to "liga1.html",
        "LIGA 1 MAX" to "liga1max.html",
        "FOX SPORTS PREMIUM" to "foxsportspremium.html",
        "MOVISTAR DEPORTES PE" to "movistardeportes.html",
        "DAZN F1" to "daznf1.html",
        "DAZN LA LIGA" to "daznlaliga.html",
        "M. LIGA DE CAMPEONES" to "movistarligadecampeones.html",
        "WIN SPORTS" to "winsports.html",
        "WIN SPORTS PLUS" to "winsportsplus.html",
        "BEIN SPORTS XTRA" to "beinsportsxtra.html",
        "MOVISTAR LA LIGA" to "movistarlaliga.html",
        "NBA" to "nba.html",
        "MOVISTAR DEPORTES ES" to "movistardeporteses.html",
        "AZTECA DEPORTES" to "aztecadeportes.html",
        "TNT SPORTS CHILE" to "tntsportschile.html",
        "MLB NETWORK" to "mlbnetwork.html",
        "SKY SPORTS MX" to "skysportsmexico.html",
        "SKY SPORTS BUNDESLIGA" to "skysportsbundesliga.html",
        "SKY SPORTS F1" to "skysportsf1.html"
    )

    private val REGIONALES_LIST = listOf(
        "AZTECA 7" to "azteca7.html",
        "CANAL 5" to "canal5.html",
        "LATINA" to "latina.html",
        "AMERICA TV" to "americatv.html",
        "SPACE" to "space.html",
        "WARNER BROS TV" to "warnerchannel.html",
        "TNT" to "tnt.html",
        "STAR CHANNEL" to "starchannel.html",
        "CINEMAX" to "cinemax.html",
        "CINECANAL" to "cinecanal.html",
        "TELEFE" to "telefe.html",
        "EL TRECE" to "eltrece.html",
        "DISTRITO COMEDIA" to "distritocomedia.html",
        "TELEMUNDO MIAMI" to "telemundo51.html",
        "HISTORY" to "history.html",
        "HISTORY 2" to "history2.html",
        "PASIONES" to "pasiones.html",
        "AZTECA UNO" to "aztecauno.html",
        "GALAVISION" to "galavision.html",
        "TLNOVELAS" to "tlnovelas.html",
        "LAS ESTRELLAS" to "lasestrellas.html",
        "SYFY USA" to "syfy.html",
        "CARACOL" to "caracol.html",
        "ATV" to "atv.html",
        "UNIVISION" to "univision.html",
        "UNICABLE" to "unicable.html",
        "FX" to "fx.html",
        "GOLDEN PLUS" to "goldenplus.html",
        "GOLDEN EDGE" to "goldenedge.html",
        "GOLDEN PREMIERE" to "goldenpremier.html",
        "TNT SERIES" to "tntseries.html",
        "CANAL SONY" to "sony.html",
        "AXN" to "axn.html",
        "UNIVERSAL TV" to "universalchannel.html",
        "STUDIO UNIVERSAL" to "studiouniversal.html",
        "MULTIPREMIER" to "multipremier.html",
        "AMC" to "amc.html",
        "NAT GEO" to "natgeo.html",
        "ANIMAL PLANET" to "animalplanet.html",
        "DISCOVERY CHANNEL" to "discoverychannel.html",
        "DISCOVERY HYH" to "discoveryhyh.html",
        "ID INVESTIGATION" to "idinvestigation.html",
        "DISCOVERY A&E" to "discoveryaye.html",
        "DISCOVERY WORLD" to "discoveryworld.html",
        "EVERYTHING" to "everything.html",
        "CARTOON NETWORK" to "cartoonnetwork.html",
        "TOONCAST" to "tooncast.html",
        "TELEMUNDO PUERTO RICO" to "telemundopuertorico.html",
        "RCN" to "rcn.html",
        "ANTENA 3" to "antena3.html",
        "DISNEY CHANNEL" to "disneychannel.html",
        "TNT NOVELAS" to "tntnovelas.html",
        "FM HOT KIDS" to "fmhotkids.html"
    )

    val channels: List<UiChannel> by lazy {
        val list = mutableListOf<UiChannel>()
        
        DEPORTES_LIST.forEach { (name, htmlPath) ->
            val iframeStr = """<iframe src="https://embed.saohgdasregions.fun/embed/$htmlPath" width="100%" height="100%" scrolling="no" frameborder="0" allowfullscreen="true"></iframe>"""
            list.add(
                UiChannel(
                    name = name,
                    groupTitle = "TV",
                    logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=150",
                    primaryStreamUrl = iframeStr,
                    sources = listOf(ChannelSource("BloodersTv Embed", iframeStr)),
                    originalGroup = "DEPORTES",
                    isEmbedText = true,
                    synopsis = "Transmisión en directo del canal deportivo $name incorporado en alta definición en BloodersTV.",
                    rating = "8.6",
                    year = "2026",
                    director = "BloodersTv",
                    actors = "Deportes en Vivo"
                )
            )
        }

        REGIONALES_LIST.forEach { (name, htmlPath) ->
            val iframeStr = """<iframe src="https://embed.saohgdasregions.fun/embed/$htmlPath" width="100%" height="100%" scrolling="no" frameborder="0" allowfullscreen="true"></iframe>"""
            list.add(
                UiChannel(
                    name = name,
                    groupTitle = "TV",
                    logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=150",
                    primaryStreamUrl = iframeStr,
                    sources = listOf(ChannelSource("BloodersTv Embed", iframeStr)),
                    originalGroup = "REGIONALES",
                    isEmbedText = true,
                    synopsis = "Canal regional $name disponible para la tripulación de BloodersTV.",
                    rating = "8.5",
                    year = "2026",
                    director = "BloodersTv",
                    actors = "Regional en Vivo"
                )
            )
        }

        list
    }
}
