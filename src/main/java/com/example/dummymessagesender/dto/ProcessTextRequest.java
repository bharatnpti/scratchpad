package java.com.example.dummymessagesender.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProcessTextRequest {
    private String textInput;
    private String userPlatformId;
    private String conversationPlatformId;
    private String sourcePlatform;
}
