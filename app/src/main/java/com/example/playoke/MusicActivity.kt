package com.example.playoke

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.playoke.databinding.ActivityMusicBinding
import com.bumptech.glide.Glide

class MusicActivity : AppCompatActivity() {
    private var musicService: MusicService? = null
    private var isBound = false
    private var handler: Handler? = null
    private lateinit var binding: ActivityMusicBinding

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicService.LocalBinder
            musicService = binder.getService()
            isBound = true

            val isRestart = intent.getBooleanExtra("restart", false)
            Log.d("testing", "${isRestart}")
            if (isRestart) {
                var position = musicService?.fetchCurrentPosition()
                Log.d("testing", "Current position before reset: $position")

                // Stop and reset the player
                musicService?.player?.stop()
                musicService?.player?.prepare() // Re-prepare the player
                musicService?.player?.seekTo(0) // Seek to the beginning

                // Reset other components
                musicService?.currentPosition = 0
                stopSeekBarUpdate()
                binding.seekBar.progress = 0
                startSeekBarUpdate()
                binding.seekBar.invalidate()

                position = musicService?.fetchCurrentPosition()
                Log.d("testing", "Current position after reset: $position")
                Log.d("testing", "Seekbar Progress: ${binding.seekBar.progress}, MediaPlayer Progress: ${musicService?.player?.currentPosition}")

                musicService?.let {
                    it.setMediaPlayer(
                        this@MusicActivity,
                        binding.musicName,
                        binding.artistName,
                        binding.musicImg,
                        binding.musicDuration,
                        binding.seekBar,
                        true
                    )
                }
            }
            updateMusicDuration()
            startSeekBarUpdate()
            if (!(musicService?.isPlaying()?:false)){
                binding.playPauseBtn.setImageResource(R.drawable.ic_play_circle_outline)
            } else{
                binding.playPauseBtn.setImageResource(R.drawable.ic_pause_circle_outline)
            }

            musicService?.setMediaPlayer(this@MusicActivity, binding.musicName, binding.artistName, binding.musicImg, binding.musicDuration, binding.seekBar, false)
            musicService?.player?.setOnCompletionListener {
                 if (musicService?.fetchCurrentPosition() != 0) { // Ensure it was playing before triggering
                    Log.d("MusicService", "Track completed. Moving to the next track.")
                    musicService?.nextMusic(
                        this@MusicActivity, binding.musicName, binding.artistName, binding.musicImg, binding.musicDuration, binding.seekBar
                    )
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            musicService = null
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        binding = ActivityMusicBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handler = Handler(Looper.getMainLooper())

        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        musicService?.setMediaPlayer(this@MusicActivity, binding.musicName, binding.artistName, binding.musicImg, binding.musicDuration, binding.seekBar, true)

        binding.idBtnDismiss.setOnClickListener{
            val intent: Intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        binding.skipPrevious.setOnClickListener{
            if(UserInfo.selectedPlaylist!="") {
                musicService?.previousMusic(
                    this,
                    binding.musicName,
                    binding.artistName,
                    binding.musicImg,
                    binding.musicDuration,
                    binding.seekBar
                )
                binding.playPauseBtn.setImageResource(R.drawable.ic_pause_circle_outline)
            }
        }
        binding.addToPlaylist.setOnClickListener{
            val intent:Intent = Intent(this,AddToPlaylistActivity::class.java)
            startActivity(intent)
        }
        binding.skipNext.setOnClickListener{
            if(UserInfo.selectedPlaylist!=""){
                musicService?.nextMusic(this, binding.musicName, binding.artistName, binding.musicImg, binding.musicDuration, binding.seekBar)
                binding.playPauseBtn.setImageResource(R.drawable.ic_pause_circle_outline)
            }
        }
        binding.lyrics.setOnClickListener{
            val intent: Intent = Intent(this, LyricsActivity::class.java)
            startActivity(intent)
        }

        binding.playPauseBtn.setOnClickListener{
            if (!(musicService?.isPlaying()?:false)){
                musicService?.startMusic()
                binding.playPauseBtn.setImageResource(R.drawable.ic_pause_circle_outline)
                startSeekBarUpdate()
            } else{
                musicService?.pauseMusic()
                binding.playPauseBtn.setImageResource(R.drawable.ic_play_circle_outline)
                stopSeekBarUpdate()
            }
        }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                stopSeekBarUpdate() // Pause updates while user interacts
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                startSeekBarUpdate() // Resume updates after interaction
            }
        })
    }
    private fun updateMusicDuration(){
        val duration = musicService?.getDurationText()?:"0:00"
        binding.musicDuration.setText(duration)
        binding.seekBar.max = musicService?.getDuration()?:0
    }
    private fun startSeekBarUpdate() {
        handler?.post(object : Runnable {
            override fun run() {
                musicService?.let {
                    val currentPosition = it.fetchCurrentPosition()
                    binding.seekBar.progress = currentPosition

                    // Update elapsed time
                    val minutes = currentPosition / 60000
                    val seconds = (currentPosition % 60000) / 1000
                    binding.elapsedTime.setText(String.format("%d:%02d", minutes, seconds))
                }
                handler?.postDelayed(this, 1000) // Update every second
            }
        })
    }

    private fun stopSeekBarUpdate() {
        handler?.removeCallbacksAndMessages(null)
    }
}