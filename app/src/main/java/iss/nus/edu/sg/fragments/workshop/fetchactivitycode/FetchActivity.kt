package iss.nus.edu.sg.fragments.workshop.fetchactivitycode

import android.content.Intent
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future


class FetchActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var selectedNumTextView: TextView

    private val imageList = mutableListOf<String>() // 保存图片文件路径的列表
    private val selectedImagesList = mutableListOf<String>() // 保存用户选择的图片文件路径的列表

    private val executor = Executors.newSingleThreadExecutor()
    private var currentTask: Future<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fetch)

        // 初始化视图
        urlInput = findViewById(R.id.urlInput)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        selectedNumTextView = findViewById(R.id.SelectedNum)

        // 设置 RecyclerView 和 Adapter
        recyclerView.layoutManager = GridLayoutManager(this, 4)
        imageAdapter = ImageAdapter(imageList)
        recyclerView.adapter = imageAdapter

        // 设置 Fetch 按钮监听器
        findViewById<Button>(R.id.fetchButton).setOnClickListener {
            val url = urlInput.text.toString()

            if (url.isBlank() || !Patterns.WEB_URL.matcher(url).matches()) {
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 取消当前下载任务
            currentTask?.cancel(true)

            // 清空 UI
            clearDownloadState()

            // 开始新的下载任务
            fetchImagesFromWebPage(url)
        }
    }

    // 清空下载状态
    private fun clearDownloadState() {
        imageList.clear()
        selectedImagesList.clear()
        imageAdapter.notifyDataSetChanged()
        progressBar.visibility = View.GONE
        progressText.visibility = View.GONE
        updateSelectedCount()
    }

    private fun fetchImagesFromWebPage(webPageUrl: String) {
        currentTask = executor.submit {
            try {
                val document = Jsoup.connect(webPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                val imageElements = document.select("img[src]")
                val imageUrls = imageElements.mapNotNull { it.attr("abs:src") }.take(20)

                runOnUiThread {
                    progressBar.max = imageUrls.size
                    progressBar.progress = 0
                    progressBar.visibility = View.VISIBLE
                    progressText.text = "Downloading 0 of ${imageUrls.size} images..."
                    progressText.visibility = View.VISIBLE
                }

                for ((index, imageUrl) in imageUrls.withIndex()) {
                    if (Thread.currentThread().isInterrupted) {
                        break
                    }

                    val ext = getFileExtension(imageUrl)
                    val file = createDestFile(ext)

                    if (downloadImage(imageUrl, file)) {
                        runOnUiThread {
                            imageList.add(file.absolutePath)
                            imageAdapter.notifyDataSetChanged()

                            progressBar.progress = index + 1
                            progressText.text = "Downloading ${index + 1} of ${imageUrls.size} images..."
                        }
                    }
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to fetch images from the webpage", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                }
            }
        }
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
                holder.imageView.setOnClickListener {
                    if (selectedImagesList.contains(filePath)) {
                        selectedImagesList.remove(filePath)
                        holder.imageView.setImageBitmap(bitmap) // Reset image
                    } else {
                        if (selectedImagesList.size < 6) {
                            selectedImagesList.add(filePath)
                            holder.imageView.setImageResource(android.R.drawable.checkbox_on_background) // Indicate selection
                        } else {
                            Toast.makeText(this@FetchActivity, "You can select up to 6 images", Toast.LENGTH_SHORT).show()
                        }
                    }
                    updateSelectedCount()
                }
            } else {
                println("Failed to decode image from path: $filePath")
            }
        }

        override fun getItemCount(): Int = images.size
    }

    private fun updateSelectedCount() {
        runOnUiThread {
            selectedNumTextView.text = "Selected: ${selectedImagesList.size}/6"
            //当选中数量达到6个，转跳到PlayActivity
//            if (selectedImagesList.size == 6) {
//                // 启动 PlayActivity
//                val intent = Intent(this, PlayActivity::class.java)
//                intent.putStringArrayListExtra("selectedImages", ArrayList(selectedImagesList))
//                startActivity(intent)
//            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
