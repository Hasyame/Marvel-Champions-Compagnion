# R8 is enabled for release. Most libraries here ship their own consumer rules
# (Room, Hilt, Coil, OkHttp, Retrofit), so what follows is only what this app
# needs on top of those.

# --- kotlinx.serialization -------------------------------------------------
# The compiler plugin generates a Companion.serializer() for every @Serializable
# class, found by name at runtime for polymorphic types. Renaming or removing
# those breaks campaign templates and the event log with a confusing runtime
# error rather than a build failure, so they are kept explicitly.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}

# The DTOs and campaign schema are deserialised by name from JSON.
-keep,includedescriptorclasses class com.hasyame.marvelchampions.data.marvelcdb.dto.** { *; }
-keep,includedescriptorclasses class com.hasyame.marvelchampions.domain.campaign.template.** { *; }
-keep,includedescriptorclasses class com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent { *; }
-keep,includedescriptorclasses class com.hasyame.marvelchampions.domain.campaign.engine.CampaignEvent$* { *; }
-keep,includedescriptorclasses class com.hasyame.marvelchampions.domain.campaign.engine.AnswerSet { *; }
-keep,includedescriptorclasses class com.hasyame.marvelchampions.domain.campaign.engine.CampaignHero { *; }

# Navigation Compose builds type-safe routes from @Serializable objects.
-keep,includedescriptorclasses class com.hasyame.marvelchampions.ui.navigation.** { *; }

# --- Retrofit --------------------------------------------------------------
# Suspend functions carry their return type in a generic signature R8 must not
# erase.
-keep,allowobfuscation interface com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbApi
-keepattributes Exceptions
