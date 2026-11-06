package com.wpreborn.data.store

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.wpreborn.R

enum class GameGenre(@StringRes val label: Int) {
    Farm(R.string.store_genre_farm),
    Puzzle(R.string.store_genre_puzzle),
    Cards(R.string.store_genre_cards),
    Arcade(R.string.store_genre_arcade),
    Racing(R.string.store_genre_racing),
    Online(R.string.store_genre_online),
}

/**
 * A real game of the Games section of the Store. They are other companies' games, so Mozaik
 * doesn't carry them: the Store lists them with their name, maker and description, and gets
 * them from the official Google Play page. Once installed they show on Start and in the app list
 * with their own icon.
 */
data class GameApp(
    /** The Android package, e.g. com.king.candycrushsaga. */
    val id: String,
    val name: String,
    val developer: String,
    val genre: GameGenre,
    /** Tile colour while the game isn't installed (its own icon is used afterwards). */
    val color: Color,
    val en: String,
    val pt: String,
    val paid: Boolean = false,
) {
    fun description(portuguese: Boolean) = if (portuguese) pt else en

    /** Two letters for the tile before the game is installed. */
    val initials: String
        get() = name.split(' ', ':', '-').filter { it.firstOrNull()?.isLetterOrDigit() == true }.take(2).joinToString("") { it.first().uppercase() }
}

object GamesCatalog {
    val games: List<GameApp> = listOf(
        // Farm
        GameApp("com.supercell.hayday", "Hay Day", "Supercell", GameGenre.Farm, Color(0xFF60A917),
            "Build your farm: grow crops, raise animals, bake and trade with friends and neighbours.",
            "Monte sua fazendinha: plante, crie animais, produza e negocie com amigos e vizinhos."),
        GameApp("com.playrix.township", "Township", "Playrix", GameGenre.Farm, Color(0xFF008A00),
            "Farm and city in one: grow your crops, run the factories and build the town of your dreams.",
            "Fazenda e cidade em um só jogo: plante, faça funcionar as fábricas e construa a cidade dos seus sonhos."),
        GameApp("com.king.farmheroessaga", "Farm Heroes Saga", "King", GameGenre.Farm, Color(0xFFA4C400),
            "Match the crops and save the farm from Rancid the Racoon in hundreds of puzzle levels.",
            "Combine as colheitas e salve a fazenda do gambá Rancid em centenas de fases de quebra-cabeça."),
        GameApp("com.chucklefish.stardewvalley", "Stardew Valley", "ConcernedApe", GameGenre.Farm, Color(0xFF3E8E41),
            "You inherit your grandfather's old farm: farm, fish, mine, make friends and fall in love.",
            "Você herda a velha fazenda do seu avô: plante, pesque, minere, faça amigos e se apaixone.", paid = true),
        GameApp("com.playrix.gardenscapes", "Gardenscapes", "Playrix", GameGenre.Farm, Color(0xFF00ABA9),
            "Restore a beautiful garden by matching and solving puzzles, with Austin the butler.",
            "Restaure um jardim lindo combinando peças e resolvendo quebra-cabeças, com o mordomo Austin."),
        // Puzzle
        GameApp("com.king.candycrushsaga", "Candy Crush Saga", "King", GameGenre.Puzzle, Color(0xFFD80073),
            "Switch and match candies in the sweetest puzzle game, with thousands of levels.",
            "Troque e combine doces no jogo de quebra-cabeça mais doce, com milhares de fases."),
        GameApp("com.king.candycrushsodasaga", "Candy Crush Soda Saga", "King", GameGenre.Puzzle, Color(0xFF1BA1E2),
            "Soda, bears and fizzy puzzles: switch and match to free the candy bears.",
            "Refrigerante, ursinhos e quebra-cabeças borbulhantes: combine para libertar os ursinhos."),
        GameApp("com.king.bubblewitch3", "Bubble Witch 3 Saga", "King", GameGenre.Puzzle, Color(0xFF6A00FF),
            "Pop bubbles with Stella the witch and her cat Wiz in a magical bubble shooter.",
            "Estoure bolhas com a bruxa Stella e seu gato Wiz num atirador de bolhas mágico."),
        GameApp("net.peakgames.toonblast", "Toon Blast", "Peak", GameGenre.Puzzle, Color(0xFFFA6800),
            "Blast cubes with friends in a colourful puzzle adventure of explosive combos.",
            "Exploda cubos com os amigos numa aventura colorida de quebra-cabeças com combos explosivos."),
        GameApp("com.playrix.homescapes", "Homescapes", "Playrix", GameGenre.Puzzle, Color(0xFFE51400),
            "Help Austin renovate his family's mansion by solving match-3 puzzles.",
            "Ajude Austin a reformar a mansão da família resolvendo quebra-cabeças de combinar peças."),
        GameApp("com.ustwo.monumentvalley", "Monument Valley", "ustwo games", GameGenre.Puzzle, Color(0xFF00ABA9),
            "Guide a silent princess through impossible architecture and optical illusions.",
            "Guie uma princesa silenciosa por uma arquitetura impossível e ilusões de ótica.", paid = true),
        // Cards + words (Zynga and friends)
        GameApp("com.zynga.words3", "Words With Friends 2", "Zynga", GameGenre.Cards, Color(0xFF0050EF),
            "Play the classic word game with friends and family, one word at a time.",
            "Jogue o clássico jogo de palavras com amigos e família, uma palavra de cada vez."),
        GameApp("com.zynga.livepoker", "Zynga Poker", "Zynga", GameGenre.Cards, Color(0xFF008A00),
            "Texas Hold'em with millions of players: tables, tournaments and friends.",
            "Texas Hold'em com milhões de jogadores: mesas, torneios e amigos."),
        GameApp("com.ludo.king", "Ludo King", "Gametion", GameGenre.Cards, Color(0xFFF0A30A),
            "The classic board game for two to four players, online or with family on one phone.",
            "O clássico jogo de tabuleiro para dois a quatro jogadores, online ou com a família no mesmo celular."),
        GameApp("com.miniclip.eightballpool", "8 Ball Pool", "Miniclip", GameGenre.Cards, Color(0xFF1E88E5),
            "Play pool against players from all over the world and climb the leagues.",
            "Jogue sinuca contra jogadores do mundo todo e suba nas ligas."),
        // Arcade
        GameApp("com.kiloo.subwaysurf", "Subway Surfers", "SYBO", GameGenre.Arcade, Color(0xFF00ABA9),
            "Dash through the rails, dodge the trains and get away from the inspector.",
            "Corra pelos trilhos, desvie dos trens e fuja do inspetor."),
        GameApp("com.imangi.templerun2", "Temple Run 2", "Imangi Studios", GameGenre.Arcade, Color(0xFFA0522D),
            "Run, jump, turn and slide your way out of the temple with the idol.",
            "Corra, pule, vire e deslize para escapar do templo com o ídolo."),
        GameApp("com.halfbrick.fruitninja", "Fruit Ninja", "Halfbrick", GameGenre.Arcade, Color(0xFFE51400),
            "Slice the fruit, dodge the bombs and become a ninja master.",
            "Fatie as frutas, desvie das bombas e vire um mestre ninja."),
        GameApp("com.halfbrick.jetpackjoyride", "Jetpack Joyride", "Halfbrick", GameGenre.Arcade, Color(0xFF515C6B),
            "Fly with Barry Steakfries through the lab, collecting coins and dodging lasers.",
            "Voe com Barry Steakfries pelo laboratório, pegando moedas e desviando de lasers."),
        GameApp("com.yodo1.crossyroad", "Crossy Road", "Hipster Whale", GameGenre.Arcade, Color(0xFF60A917),
            "Why did the chicken cross the road? Hop through traffic, rivers and rails.",
            "Por que a galinha atravessou a rua? Pule por trânsito, rios e trilhos."),
        GameApp("com.rovio.baba", "Angry Birds 2", "Rovio", GameGenre.Arcade, Color(0xFFE51400),
            "Fling the birds at the pigs in levels with several stages and spells.",
            "Lance os pássaros nos porcos em fases com várias etapas e magias."),
        GameApp("com.ea.game.pvz2_row", "Plants vs. Zombies 2", "Electronic Arts", GameGenre.Arcade, Color(0xFF60A917),
            "Defend your lawn across time with plants against the zombie horde.",
            "Defenda seu gramado através do tempo com plantas contra a horda de zumbis."),
        GameApp("me.pou.app", "Pou", "Zakeh", GameGenre.Arcade, Color(0xFF7B4A12),
            "Adopt and take care of your alien pet: feed it, bathe it and play mini-games.",
            "Adote e cuide do seu bichinho alienígena: alimente, dê banho e jogue minijogos."),
        GameApp("com.outfit7.mytalkingtomfree", "My Talking Tom", "Outfit7", GameGenre.Arcade, Color(0xFF1BA1E2),
            "Take care of Tom the cat: feed him, play with him and he repeats what you say.",
            "Cuide do gato Tom: alimente, brinque e ele repete o que você disser."),
        // Racing
        GameApp("com.fingersoft.hillclimb", "Hill Climb Racing", "Fingersoft", GameGenre.Racing, Color(0xFFFA6800),
            "Drive up hills with physics and funny vehicles, collecting coins and fuel.",
            "Suba ladeiras com física e veículos engraçados, pegando moedas e combustível."),
        GameApp("com.gameloft.android.ANMP.GloftA9HM", "Asphalt 9: Legends", "Gameloft", GameGenre.Racing, Color(0xFFE51400),
            "Arcade racing with real supercars: drift, boost and take on the world.",
            "Corrida arcade com superesportivos de verdade: derrape, acelere e enfrente o mundo."),
        // Online + strategy
        GameApp("com.supercell.clashofclans", "Clash of Clans", "Supercell", GameGenre.Online, Color(0xFFF0A30A),
            "Build your village, train your troops and battle with clans from around the world.",
            "Construa sua vila, treine suas tropas e batalhe com clãs do mundo todo."),
        GameApp("com.supercell.brawlstars", "Brawl Stars", "Supercell", GameGenre.Online, Color(0xFFFA6800),
            "Fast 3v3 battles and battle royale with dozens of brawlers.",
            "Batalhas rápidas 3x3 e battle royale com dezenas de lutadores."),
        GameApp("com.mojang.minecraftpe", "Minecraft", "Mojang", GameGenre.Online, Color(0xFF60A917),
            "Explore infinite worlds and build anything, from simple houses to grand castles.",
            "Explore mundos infinitos e construa de tudo, de casinhas a grandes castelos.", paid = true),
        GameApp("com.innersloth.spacemafia", "Among Us", "Innersloth", GameGenre.Online, Color(0xFFE51400),
            "Work together on the ship and find the impostor before it's too late.",
            "Trabalhem juntos na nave e descubram o impostor antes que seja tarde."),
        GameApp("com.kitkagames.fallbuddies", "Stumble Guys", "Scopely", GameGenre.Online, Color(0xFF8E44AD),
            "Race through obstacle courses with up to 32 players: the last one standing wins.",
            "Corra por percursos de obstáculos com até 32 jogadores: o último de pé vence."),
        GameApp("com.roblox.client", "Roblox", "Roblox", GameGenre.Online, Color(0xFF515C6B),
            "Millions of games and worlds made by the community, to play with friends.",
            "Milhões de jogos e mundos feitos pela comunidade, para jogar com os amigos."),
        GameApp("com.dts.freefireth", "Free Fire", "Garena", GameGenre.Online, Color(0xFFFA6800),
            "Ten-minute battle royale: survive on an island against 49 other players.",
            "Battle royale de dez minutos: sobreviva numa ilha contra outros 49 jogadores."),
    )

    fun byId(id: String) = games.firstOrNull { it.id == id }
}
