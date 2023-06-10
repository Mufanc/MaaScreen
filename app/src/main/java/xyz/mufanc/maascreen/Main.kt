package xyz.mufanc.maascreen

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val controller = Controller()
        controller.start()
        controller.join()
    }
}
