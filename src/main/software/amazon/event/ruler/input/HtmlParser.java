package software.amazon.event.ruler.input;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static software.amazon.event.ruler.input.DefaultParser.CLOSE_HTML_BRACKET_BYTE;
import static software.amazon.event.ruler.input.DefaultParser.OPEN_HTML_BRACKET_BYTE;

public class HtmlParser {

    /**
     * Constructor
     */
    HtmlParser(){

    }

    /**
     * WIP:: Function responsible for parsing the value from the custom HTML rule
     * @param value: The html value of the rule
     * @return
     */
    public InputCharacter[] parse(String value){
        String valueWoQuotes = value.replaceAll("^\"|\"$", "");
        List<InputCharacter> result = new ArrayList<>(valueWoQuotes.length());

        final byte[] utf8Bytes = valueWoQuotes.getBytes(StandardCharsets.UTF_8);
        // Checks if the first char is a '<' and the last char is '>'. Example <head>
        if(utf8Bytes[0] == 60 && utf8Bytes[utf8Bytes.length -1] == 62){

            for (int i = 0; i < utf8Bytes.length; i++) {
                byte utf8byte = utf8Bytes[i];
                result.add(new InputByte(utf8byte));
            }
        }
        return result.toArray(new InputCharacter[0]);
    }
}
