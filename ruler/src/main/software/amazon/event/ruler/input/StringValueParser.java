package software.amazon.event.ruler.input;

public interface StringValueParser {

  /**
   * @param value string value to parse
   * @return processed and parsed Input Character
   */
  InputCharacter[] parse(String value);
}