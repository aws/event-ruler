package software.amazon.event.ruler.input;

import software.amazon.event.ruler.MatchType;

public interface MatchTypeParser {

  /**
   * @param type Match type
   * @param value string value to parse
   * @return processed and parsed Input Character
   */
  InputCharacter[] parse(MatchType type, String value);
}