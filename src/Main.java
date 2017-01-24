import com.hyunjae.xdcc2.Bot;

public class Main {

    public static void main(String[] args) throws Exception{
        Bot bot = new Bot("irc.rizon.net", "bot", new String[]{"#nipponsei"});
        bot.start();
    }
}
