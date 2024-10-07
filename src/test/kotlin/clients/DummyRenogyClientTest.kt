package clients

import org.junit.jupiter.api.Test

class DummyRenogyClientTest {
    @Test fun smoke() {
        val client = DummyRenogyClient()
        client.getAllData()
        client.getAllData()
        Thread.sleep(10)
        client.getAllData()
    }
}
