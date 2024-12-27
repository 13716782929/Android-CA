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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FetchActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var selectedNumTextView: TextView

    private val imageList = mutableListOf<String>() // 保存图片文件路径的列表
    private val selectedImagesList = mutableListOf<String>() // 保存用户选择的图片文件路径的列表

    private var executor = Executors.newSingleThreadExecutor()
    private var currentTaskId: UUID? = null // 当前任务的唯一标识符
    private var imageCounter = 0 // 图片计数器

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

            // 取消当前任务
            currentTaskId = UUID.randomUUID() // 创建新的任务 ID
            clearDownloadState() // 清空 UI
            fetchImagesFromWebPage(url, currentTaskId!!) // 启动新的下载任务
        }
    }

    // 清空下载状态
    private fun clearDownloadState() {
        currentTaskId = UUID.randomUUID() // 创建新任务的 ID
        executor.shutdownNow() // 停止当前所有线程
        executor.awaitTermination(2, TimeUnit.SECONDS) // 等待线程停止
        executor = Executors.newSingleThreadExecutor() // 重新创建线程池

        runOnUiThread {
            imageList.clear()
            selectedImagesList.clear()
            imageAdapter.notifyDataSetChanged()
            progressBar.visibility = View.GONE
            progressText.visibility = View.GONE
            updateSelectedCount()
            imageCounter = 0 // 重置图片计数器
        }
    }

    private fun clearDownloadDir(){

    }

    private fun fetchImagesFromWebPage(webPageUrl: String, taskId: UUID) {
        executor.submit {
            try {
                val document = Jsoup.connect(webPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .get()
                // 过滤掉 .svg 格式的图片
                val imageElements = document.select("img[src]")
                val imageUrls = imageElements
                    .mapNotNull { it.attr("abs:src") }
                    .filter { !it.endsWith(".svg", ignoreCase = true) } // 排除 .svg 文件
                    .distinct()
                    .take(20)

                runOnUiThread {
                    progressBar.max = imageUrls.size
                    progressBar.progress = 0
                    progressBar.visibility = View.VISIBLE
                    progressText.text = "Downloading 0 of ${imageUrls.size} images..."
                    progressText.visibility = View.VISIBLE
                }

                for ((index, imageUrl) in imageUrls.withIndex()) {
                    if (currentTaskId != taskId) { // 检查是否是当前任务
                        break
                    }

                    val file = createDestFile() // 使用新的命名规则

                    if (downloadImage(imageUrl, file)) {
                        runOnUiThread {
                            if (currentTaskId == taskId && imageList.size < 20) { // 确保列表大小
                                imageList.add(file.absolutePath)
                                imageAdapter.notifyItemInserted(imageList.size - 1)

                                progressBar.progress = index + 1
                                progressText.text = "Downloading ${index + 1} of ${imageUrls.size} images..."
                            }
                        }
                    }
                }

                if (currentTaskId == taskId) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        progressText.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    if (currentTaskId == taskId) {
                        Toast.makeText(this, "Failed to fetch images from the webpage", Toast.LENGTH_SHORT).show()
                        progressBar.visibility = View.GONE
                        progressText.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun createDestFile(): File {
        val filename = "image_${++imageCounter}.jpg" // 生成有序文件名
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(dir, filename)
    }

    private fun downloadImage(imgURL: String, file: File): Boolean {
        var conn: HttpURLConnection? = null
        var inp: InputStream? = null
        var outp: FileOutputStream? = null

        return try {
            val url = URL(imgURL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                inp = conn.inputStream
                outp = FileOutputStream(file)

                val buf = ByteArray(4096)
                var bytesRead = inp.read(buf)

                while (bytesRead != -1) {
                    if (Thread.interrupted()) { // 检查是否被中断
                        throw InterruptedException("Download interrupted")
                    }
                    outp.write(buf, 0, bytesRead)
                    bytesRead = inp.read(buf)
                }
                true
            } else {
                false
            }
        } catch (e: InterruptedException) {
            file.delete()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            inp?.close()
            outp?.close()
            conn?.disconnect()
        }
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }
}
