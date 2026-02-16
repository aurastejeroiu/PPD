import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@AllArgsConstructor
public class Node {
    private int id;
    @ToString.Exclude
    private List<Node> neighbours;
}
