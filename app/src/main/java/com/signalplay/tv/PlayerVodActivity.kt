    // =========================================================================
    // MÁGICA: SALVAMENTO BLINDADO NO FIREBASE (À PROVA DE FALHAS)
    // =========================================================================
    private fun salvarProgressoNoFirebase() {
        val idToSave = if (tipoMedia == "serie") parentSeriesId else mediaId
        if (username.isEmpty() || idToSave.isEmpty()) return
        
        val position = exoPlayer?.currentPosition ?: 0L
        val duration = exoPlayer?.duration ?: 0L

        // Só salva se passou de 10 segundos
        if (position > 10000L && duration > 0L) { 
            db.collection("usuarios").whereEqualTo("usuario", username).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        val docId = snapshot.documents[0].id
                        val docRef = db.collection("usuarios").document(docId)
                        
                        // Tenta ATUALIZAR o filme específico
                        docRef.update("historico_vod.$idToSave", mapOf("posicao" to position, "duracao" to duration))
                            .addOnFailureListener {
                                // PLANO B: Se falhar porque a pasta 'historico_vod' nunca foi criada, ele cria agora!
                                val dadosIniciais = mapOf("historico_vod" to mapOf(idToSave to mapOf("posicao" to position, "duracao" to duration)))
                                docRef.set(dadosIniciais, SetOptions.merge())
                            }
                    }
                }
        }
    }

    override fun onPause() {
        super.onPause()
        salvarProgressoNoFirebase()
        exoPlayer?.pause()
        countDownTimer?.cancel()
    }

    // Se o cliente apertar o botão Home da TV e o app for minimizado
    override fun onStop() {
        super.onStop()
        salvarProgressoNoFirebase()
    }

    override fun onDestroy() {
        super.onDestroy()
        salvarProgressoNoFirebase()
        introHandler.removeCallbacks(introRunnable)
        countDownTimer?.cancel()
        exoPlayer?.release()
    }
