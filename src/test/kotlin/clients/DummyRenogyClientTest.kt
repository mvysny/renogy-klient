package clients

import com.github.mvysny.dynatest.DynaTest

class DummyRenogyClientTest : DynaTest({
    test("smoke") {
        val client = DummyRenogyClient()
        client.getAllData()
        client.getAllData()
        Thread.sleep(10)
        client.getAllData()
    }
})
