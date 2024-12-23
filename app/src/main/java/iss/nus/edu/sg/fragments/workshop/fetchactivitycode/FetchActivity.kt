package iss.nus.edu.sg.fragments.workshop.fetchactivitycode

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class FetchActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter

    private val imageList = mutableListOf<String>() // 用于保存图片文件路径的列表
    private val selectedImagesList = mutableListOf<String>() // 用于保存用户选择的图片文件路径的列表

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fetch)

        urlInput = findViewById(R.id.urlInput)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        imageAdapter = ImageAdapter(imageList)
        recyclerView.adapter = imageAdapter

        findViewById<Button>(R.id.fetchButton).setOnClickListener {
            val url = urlInput.text.toString()

            if (url.isBlank() || !Patterns.WEB_URL.matcher(url).matches()) {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            imageList.clear()
            imageAdapter.notifyDataSetChanged()

            fetchImagesFromWebPage(url)
        }
    }

    // 从网页抓取图片的方法
    private fun fetchImagesFromWebPage(webPageUrl: String) {
        Thread {
            try {
                val document = Jsoup.connect(webPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                val imageElements = document.select("img[src]")
                val imageUrls = imageElements.mapNotNull { it.attr("abs:src") }.take(20)

                for (imageUrl in imageUrls) {
                    val ext = getFileExtension(imageUrl)
                    val file = createDestFile(ext)

                    if (downloadImage(imageUrl, file)) {
                        runOnUiThread {
                            imageList.add(file.absolutePath)
                            imageAdapter.notifyDataSetChanged()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to fetch images from the webpage", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun getFileExtension(str: String): String {
        return str.substringAfterLast(".", "")
    }

    private fun createDestFile(ext: String): File {
        val filename = UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else ""
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(dir, filename)
    }

    private fun downloadImage(imgURL: String, file: File): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(imgURL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                outputToFile(conn, file)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            conn?.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun outputToFile(conn: HttpURLConnection, file: File) {
        val inp = conn.inputStream
        val outp = FileOutputStream(file)

        val buf = ByteArray(4096)
        var bytesRead = inp.read(buf)

        while (bytesRead != -1) {
            outp.write(buf, 0, bytesRead)
            bytesRead = inp.read(buf)
        }

        inp.close()
        outp.close()
    }

    inner class ImageAdapter(private val images: List<String>) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.imageView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val filePath = images[position]
            val bitmap = BitmapFactory.decodeFile(filePath)

            if (bitmap != null) {
                holder.imageView.setImageBitmap(bitmap)
                holder.imageView.setOnClickListener{
                    if (selectedImagesList.contains(filePath)) {
                        selectedImagesList.remove(filePath)
                        Toast.makeText(this@FetchActivity, "Selected images: ${selectedImagesList.size}", Toast.LENGTH_SHORT).show()
                        holder.imageView.setImageBitmap(bitmap)
                        return@setOnClickListener
                    }else{
                        if(selectedImagesList.size <= 5){
                            selectedImagesList.add(filePath)
                            holder.imageView.setImageResource(android.R.drawable.checkbox_on_background)
                            Toast.makeText(this@FetchActivity, "Selected images: ${selectedImagesList.size}", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                    }

                }
            } else {
                println("Failed to decode image from path: $filePath")
            }
        }

        override fun getItemCount(): Int = images.size
    }

    fun showSelectedImages() : List<String> {
        return selectedImagesList
    }
}
