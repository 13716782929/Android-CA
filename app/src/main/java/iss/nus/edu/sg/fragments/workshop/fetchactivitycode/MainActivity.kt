package iss.nus.edu.sg.fragments.workshop.fetchactivitycode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 获取按钮并设置点击事件监听器
        findViewById<Button>(R.id.launchFetchActivityButton).setOnClickListener {
            // 跳转到FetchActivity
            val intent = Intent(this, FetchActivity::class.java)
            startActivity(intent)
        }
    }
}
