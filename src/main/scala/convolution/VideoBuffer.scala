package morphinator

import chisel3._
import chisel3.util._

class VideoBuffer(val imageWidth: Int, val imageHeight: Int, val parallelPixels: Int) extends Module {

    val io = IO(
        new Bundle {
            val pixelVal_in = Input(Vec(parallelPixels, UInt(4.W)))
            val valid_in    = Input(Bool())
			val rowIndex    = Input(UInt(11.W)) // 11 bits => 2048 adresses (2048 pixels should be enough for an image)
			val colIndex    = Input(UInt(11.W))

			val pixelVal_out = Output(UInt(4.W))
        }
    )

    val image = RegInit(VecInit(List.fill(imageWidth * imageHeight)(7.U(4.W))))
    // val image = VecInit(List.fill(imageWidth * imageHeight)(7.U(4.W))) // TODO init at 0 (test purposes)
    /*val image = VecInit(
        0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W),
        0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W),
        0.U(4.W), 0.U(4.W), 15.U(4.W), 15.U(4.W), 15.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W),
        0.U(4.W), 0.U(4.W), 15.U(4.W), 15.U(4.W), 15.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W),
        0.U(4.W), 0.U(4.W), 15.U(4.W), 15.U(4.W), 15.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W),
        0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W), 0.U(4.W)
    )*/

	io.pixelVal_out := image(io.rowIndex * imageWidth.U + io.colIndex) // Not guaranteed to work, because VGA has a higher clock frequency than the FPGA

	val pixelIndex = RegInit(UInt(32.W), 0.U)

	when(io.valid_in){
		for(i <- 0 until parallelPixels){
			image(pixelIndex + i.U) := io.pixelVal_in(i)
		}
		pixelIndex := pixelIndex + parallelPixels.U
        when(pixelIndex === imageWidth.U * imageHeight.U){
            pixelIndex := 0.U
        }
	}
}
