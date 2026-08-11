# Voice recognition - Model test results

## voice-service/spikes/language-id/run_language_id.py 

Instructions on how the results to run
1. cd <project-parent-directory-path>
   2. In my case cd ~/Projects/rural-aid-platform
2. python3 -m venv voice-service/spikes/language-id/.venv-spike
3. source voice-service/spikes/language-id/.venv-spike
4. python -m pip install --upgrade pip
5. python -m pip install -r voice-service/spikes/language-id/requirements.txt
6. python voice-service/spikes/language-id/run_language_id.py test-audio/telugu-emergency-request.wav


## Voice recognition output by the model
{
  "audio_path": "/Users/hr/Projects/rural-aid-platform/test-audio/telugu-emergency-request.wav",
  "model_id": "speechbrain/lang-id-voxlingua107-ecapa",
  "predicted_language": "te: Telugu",
  "class_index": 92,
  "log_score": -0.012756885960698128,
  "confidence": 0.9873241186141968,
  "elapsed_seconds": 0.335
}

