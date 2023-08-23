package software.amazon.event.ruler.input;

import java.util.ArrayList;
import java.util.List;

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
        List<InputCharacter> result = new ArrayList<>(value.length());
        return result.toArray(new InputCharacter[0]);
    }
}
