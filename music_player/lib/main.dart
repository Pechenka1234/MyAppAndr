import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';

void main() {
  runApp(const MusicPlayerApp());
}

class MusicPlayerApp extends StatelessWidget {
  const MusicPlayerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: MusicPlayerScreen(),
    );
  }
}

class MusicPlayerScreen extends StatefulWidget {
  const MusicPlayerScreen({super.key});

  @override
  MusicPlayerScreenState createState() => MusicPlayerScreenState();
}

class MusicPlayerScreenState extends State<MusicPlayerScreen> {
  final AudioPlayer _audioPlayer = AudioPlayer();
  List<Map<String, dynamic>> _playlist = [];
  int? _currentTrackIndex;
  bool _isPlaying = false;
  SharedPreferences? _prefs;

  @override
  void initState() {
    super.initState();
    _initPreferences();
  }

  Future<void> _initPreferences() async {
    _prefs = await SharedPreferences.getInstance();
    await _prefs!.setString('test_key', 'test_value');
    print('Test key: ${_prefs!.getString('test_key')}');
    await _loadPlaylist();
    // Для веб-платформи не потрібно _restoreFiles, адже файли завантажуються при виборі
  }

  Future<void> _loadPlaylist() async {
    try {
      final String? playlistJson = _prefs?.getString('playlist');
      print('Loading playlist: $playlistJson');
      if (playlistJson != null && playlistJson.isNotEmpty) {
        final List<dynamic> savedPlaylist = jsonDecode(playlistJson);
        setState(() {
          _playlist = savedPlaylist.cast<Map<String, dynamic>>();
          // На вебі зберігаємо лише назви, файли завантажуються заново
          if (kIsWeb) {
            for (var track in _playlist) {
              track.remove('url'); // Видаляємо застарілі URL
            }
          }
        });
        print('Loaded playlist: $_playlist');
      } else {
        print('No saved playlist found');
      }
    } catch (e) {
      print('Error loading playlist: $e');
    }
  }

  Future<void> _savePlaylist() async {
    try {
      if (_prefs != null) {
        final String playlistJson = jsonEncode(_playlist);
        final bool success = await _prefs!.setString('playlist', playlistJson);
        await _prefs!.reload();
        print('Saved playlist: $playlistJson (Success: $success)');
        final String? savedJson = _prefs!.getString('playlist');
        print('Verified saved playlist: $savedJson');
      } else {
        print('SharedPreferences not initialized');
      }
    } catch (e) {
      print('Error saving playlist: $e');
    }
  }

  Future<void> _clearPlaylist() async {
    setState(() {
      _playlist.clear();
      _stopTrack();
      _savePlaylist();
    });
  }

  Future<void> _sortPlaylist() async {
    setState(() {
      _playlist.sort((a, b) => a['name'].compareTo(b['name']));
      _savePlaylist();
    });
  }

  Future<void> _pickFiles() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.audio,
      allowMultiple: true,
    );

    if (result != null && result.files.isNotEmpty) {
      setState(() {
        for (var file in result.files) {
          if (kIsWeb) {
            // На вебі зберігаємо дані файлу для відтворення
            _playlist.add({
              'name': file.name,
              'bytes': file.bytes, // Дані файлу для відтворення на вебі
            });
          } else {
            // На Android зберігаємо шлях
            if (file.path != null) {
              _playlist.add({
                'name': file.name,
                'path': file.path,
              });
            }
          }
        }
        _savePlaylist();
      });
    }
  }

  // Видаляємо _restoreFiles, адже файли завантажуються при виборі

  Future<void> _reselectFile(int index) async {
    FilePickerResult? result = await FilePicker.platform.pickFiles(
      type: FileType.audio,
    );

    if (result != null && result.files.isNotEmpty) {
      final file = result.files.first;
      setState(() {
        if (kIsWeb) {
          _playlist[index] = {
            'name': file.name,
            'bytes': file.bytes,
          };
        } else {
          _playlist[index] = {
            'name': file.name,
            'path': file.path,
          };
        }
        _savePlaylist();
      });
    }
  }

  Future<void> _playTrack(int index) async {
    if (_currentTrackIndex == index && _isPlaying) {
      await _audioPlayer.pause();
      setState(() {
        _isPlaying = false;
      });
      return;
    }

    final track = _playlist[index];
    try {
      if (kIsWeb && track['bytes'] != null) {
        await _audioPlayer.setAudioSource(
          ByteStreamAudioSource((await Future.value(track['bytes']!)).openRead()),
        );
      } else if (!kIsWeb && track['path'] != null) {
        await _audioPlayer.setFilePath(track['path']!);
      } else {
        await _reselectFile(index);
        if (kIsWeb && _playlist[index]['bytes'] == null) return;
        if (!kIsWeb && _playlist[index]['path'] == null) return;
        await _playTrack(index); // Повторний виклик після reselection
        return;
      }
      await _audioPlayer.play();
      setState(() {
        _currentTrackIndex = index;
        _isPlaying = true;
      });
    } catch (e) {
      print('Error playing track: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Failed to play ${track['name']}')),
        );
      }
    }
  }

  Future<void> _pauseTrack() async {
    await _audioPlayer.pause();
    setState(() {
      _isPlaying = false;
    });
  }

  Future<void> _stopTrack() async {
    await _audioPlayer.stop();
    setState(() {
      _isPlaying = false;
      _currentTrackIndex = null;
    });
  }

  void _deleteTrack(int index) {
    setState(() {
      _playlist.removeAt(index);
      if (_currentTrackIndex == index) {
        _stopTrack();
      } else if (_currentTrackIndex != null && _currentTrackIndex! > index) {
        _currentTrackIndex = _currentTrackIndex! - 1;
      }
      _savePlaylist();
    });
  }

  Future<void> _playNextTrack() async {
    if (_currentTrackIndex == null || _currentTrackIndex! >= _playlist.length - 1) {
      await _stopTrack();
      return;
    }
    await _playTrack(_currentTrackIndex! + 1);
  }

  Future<void> _playPreviousTrack() async {
    if (_currentTrackIndex == null || _currentTrackIndex! <= 0) {
      await _stopTrack();
      return;
    }
    await _playTrack(_currentTrackIndex! - 1);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('MP3 Player'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton(
                  onPressed: _pickFiles,
                  child: const Text('Add MP3 Files'),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _clearPlaylist,
                  child: const Text('Clear Playlist'),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _savePlaylist,
                  child: const Text('Save Playlist'),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  onPressed: _sortPlaylist,
                  child: const Text('Sort Playlist'),
                ),
              ],
            ),
          ),
          Expanded(
            child: _playlist.isEmpty
                ? const Center(child: Text('No tracks added'))
                : ListView.builder(
              itemCount: _playlist.length,
              itemBuilder: (context, index) {
                final track = _playlist[index];
                final isCurrentTrack = _currentTrackIndex == index;
                return ListTile(
                  title: Text(track['name'] ?? 'Unknown'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (isCurrentTrack && _isPlaying)
                        const Icon(Icons.play_arrow),
                      IconButton(
                        icon: const Icon(Icons.refresh),
                        onPressed: () => _reselectFile(index),
                        tooltip: 'Reselect file',
                      ),
                      IconButton(
                        icon: const Icon(Icons.delete),
                        onPressed: () => _deleteTrack(index),
                      ),
                    ],
                  ),
                  onTap: () => _playTrack(index),
                );
              },
            ),
          ),
          if (_currentTrackIndex != null) ...[
            StreamBuilder<Duration?>(
              stream: _audioPlayer.positionStream,
              builder: (context, snapshot) {
                final position = snapshot.data ?? Duration.zero;
                final duration = _audioPlayer.duration ?? Duration.zero;
                return Slider(
                  value: position.inSeconds.toDouble(),
                  max: duration.inSeconds.toDouble(),
                  onChanged: (value) {
                    _audioPlayer.seek(Duration(seconds: value.toInt()));
                  },
                );
              },
            ),
            StreamBuilder<Duration?>(
              stream: _audioPlayer.durationStream,
              builder: (context, snapshot) {
                final duration = snapshot.data ?? Duration.zero;
                final position = _audioPlayer.position ?? Duration.zero;
                return Text(
                  '${position.inMinutes}:${(position.inSeconds % 60).toString().padLeft(2, '0')} / '
                      '${duration.inMinutes}:${(duration.inSeconds % 60).toString().padLeft(2, '0')}',
                );
              },
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  IconButton(
                    icon: const Icon(Icons.skip_previous),
                    onPressed: _playPreviousTrack,
                    tooltip: 'Previous',
                  ),
                  IconButton(
                    icon: Icon(_isPlaying ? Icons.pause : Icons.play_arrow),
                    onPressed: _isPlaying ? _pauseTrack : () => _playTrack(_currentTrackIndex!),
                  ),
                  IconButton(
                    icon: const Icon(Icons.stop),
                    onPressed: _stopTrack,
                  ),
                  IconButton(
                    icon: const Icon(Icons.skip_next),
                    onPressed: _playNextTrack,
                    tooltip: 'Next',
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }
}