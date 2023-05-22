package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.UtilImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {
    Dealer dealer;
    @Mock
    private Table table;

    private UtilImpl util;
    @Mock
    private Player mockPlayer1;
    @Mock
    private Player mockPlayer2;
    Player[] mockPlayers;
    @Mock
    private UserInterface ui;

    @Mock
    private Logger logger;


    @BeforeEach
    void setUp(){
        // purposely do not find the configuration files (use defaults here).
        mockPlayers = new Player[2];
        mockPlayers[0] = mockPlayer1;
        mockPlayers[1] = mockPlayer2;
        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        Config config = new Config(logger, properties);
        util = new UtilImpl(config);
        Env env = new Env(logger, config, ui, util);
        dealer = new Dealer(env, table,mockPlayers);
    }


    @Test
    void IsPlayersDeckASetTrue() {
        List<Integer> returnedDeck = new ArrayList<>();
        for (int i = 0; i< 3 ; i++){
            returnedDeck.add(i);
        }
        when(table.getPlayersDeck(0)).thenReturn(returnedDeck);
        assertTrue(dealer.isPlayersDeckASet(0));
    }

    @Test
    void IsPlayersDeckASetFalse() {
        List<Integer> returnedDeck = new ArrayList<>();
        for (int i = 1; i< 4 ; i++){
            returnedDeck.add(i);
        }
        when(table.getPlayersDeck(0)).thenReturn(returnedDeck);
        assertFalse(dealer.isPlayersDeckASet(0));
    }

    @Test
    void getWinners() {
        when(mockPlayer1.getScore()).thenReturn(5);
        when(mockPlayer2.getScore()).thenReturn(3);
        when(mockPlayer1.getId()).thenReturn(0);
        int[] expectedWInners = {0};
        assertArrayEquals(expectedWInners,dealer.getWinners(dealer.winnersScore()));
    }

    @Test
    void getWinnersTie() {
        when(mockPlayer1.getScore()).thenReturn(88);
        when(mockPlayer2.getScore()).thenReturn(88);
        when(mockPlayer1.getId()).thenReturn(0);
        when(mockPlayer2.getId()).thenReturn(1);
        int[] expectedWInners = {0,1};
        assertArrayEquals(expectedWInners,dealer.getWinners(dealer.winnersScore()));
    }
}