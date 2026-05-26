                itemView.setOnLongClickListener {
                    val canal = listaCanais[bindingAdapterPosition]
                    val prefs = itemView.context.getSharedPreferences("SignalPlayPrefs", Context.MODE_PRIVATE)
                    val favsJson = prefs.getString("favoritos_tv", "[]")
                    val type = object : TypeToken<MutableList<String>>(){}.type
                    
                    // BLINDAGEM: Lê os favoritos de forma segura
                    val favs: MutableList<String> = try {
                        Gson().fromJson(favsJson, type) ?: mutableListOf()
                    } catch (e: Exception) {
                        prefs.edit().putString("favoritos_tv", "[]").apply()
                        mutableListOf()
                    }
                    
                    val stringId = canal.stream_id.toString()
                    if(favs.contains(stringId)) {
                        favs.remove(stringId)
                        Toast.makeText(itemView.context, "❌ Removido dos Favoritos", Toast.LENGTH_SHORT).show()
                    } else {
                        favs.add(stringId)
                        Toast.makeText(itemView.context, "⭐ Salvo nos Favoritos!", Toast.LENGTH_SHORT).show()
                    }
                    prefs.edit().putString("favoritos_tv", Gson().toJson(favs)).apply()
                    true
                }
