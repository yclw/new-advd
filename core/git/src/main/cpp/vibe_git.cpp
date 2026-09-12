#include <jni.h>

#include <git2.h>

#include <string>
#include <vector>

namespace {

constexpr const char* kNativeException = "com/aeibi/avd/core/git/GitNativeException";

std::string string_from(JNIEnv* env, jstring value) {
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring string_to(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

void throw_native(JNIEnv* env, const char* kind) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass(kNativeException);
    if (type == nullptr) return;
    jmethodID constructor = env->GetMethodID(
        type,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;)V"
    );
    if (constructor == nullptr) return;
    const git_error* error = git_error_last();
    jstring java_kind = env->NewStringUTF(kind);
    jstring message = env->NewStringUTF(error == nullptr ? "Git operation failed." : error->message);
    jobject exception = env->NewObject(type, constructor, java_kind, message);
    env->Throw(static_cast<jthrowable>(exception));
    env->DeleteLocalRef(java_kind);
    env->DeleteLocalRef(message);
    env->DeleteLocalRef(exception);
    env->DeleteLocalRef(type);
}

const char* error_kind(int code, const char* fallback = "OPERATION_FAILED") {
    if (code == GIT_ENOTFOUND) return "REPOSITORY_NOT_FOUND";
    if (code == GIT_EEXISTS) return "REPOSITORY_ALREADY_EXISTS";
    if (code == GIT_EINVALIDSPEC) return "INVALID_REVISION";
    return fallback;
}

bool open_repository(JNIEnv* env, const std::string& git_directory, git_repository** repository) {
    int result = git_repository_open(repository, git_directory.c_str());
    if (result == 0) return true;
    throw_native(env, error_kind(result));
    return false;
}

std::string oid_string(const git_oid* oid) {
    char buffer[GIT_OID_MAX_HEXSIZE + 1];
    git_oid_tostr(buffer, sizeof(buffer), oid);
    return buffer;
}

jobject make_trailer(JNIEnv* env, const git_message_trailer& trailer) {
    jclass type = env->FindClass("com/aeibi/avd/core/git/NativeGitTrailer");
    if (type == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(type, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (constructor == nullptr) return nullptr;
    jstring key = env->NewStringUTF(trailer.key);
    jstring value = env->NewStringUTF(trailer.value);
    jobject result = env->NewObject(type, constructor, key, value);
    env->DeleteLocalRef(key);
    env->DeleteLocalRef(value);
    env->DeleteLocalRef(type);
    return result;
}

jobject make_commit(JNIEnv* env, git_commit* commit) {
    jclass commit_type = env->FindClass("com/aeibi/avd/core/git/NativeGitCommit");
    jclass string_type = env->FindClass("java/lang/String");
    jclass trailer_type = env->FindClass("com/aeibi/avd/core/git/NativeGitTrailer");
    if (commit_type == nullptr || string_type == nullptr || trailer_type == nullptr) return nullptr;
    jmethodID constructor = env->GetMethodID(
        commit_type,
        "<init>",
        "(Ljava/lang/String;[Ljava/lang/String;J[Lcom/aeibi/avd/core/git/NativeGitTrailer;)V"
    );
    if (constructor == nullptr) return nullptr;

    const size_t parent_count = git_commit_parentcount(commit);
    jobjectArray parents = env->NewObjectArray(parent_count, string_type, nullptr);
    for (size_t index = 0; index < parent_count; ++index) {
        jstring parent = string_to(env, oid_string(git_commit_parent_id(commit, index)));
        env->SetObjectArrayElement(parents, index, parent);
        env->DeleteLocalRef(parent);
    }

    git_message_trailer_array trailers{};
    if (git_message_trailers(&trailers, git_commit_message_raw(commit)) != 0) {
        throw_native(env, "OPERATION_FAILED");
        return nullptr;
    }
    jobjectArray trailer_values = env->NewObjectArray(trailers.count, trailer_type, nullptr);
    for (size_t index = 0; index < trailers.count; ++index) {
        jobject trailer = make_trailer(env, trailers.trailers[index]);
        env->SetObjectArrayElement(trailer_values, index, trailer);
        env->DeleteLocalRef(trailer);
    }
    git_message_trailer_array_free(&trailers);

    jstring revision = string_to(env, oid_string(git_commit_id(commit)));
    jobject result = env->NewObject(
        commit_type,
        constructor,
        revision,
        parents,
        static_cast<jlong>(git_commit_time(commit)),
        trailer_values
    );
    env->DeleteLocalRef(revision);
    env->DeleteLocalRef(parents);
    env->DeleteLocalRef(trailer_values);
    env->DeleteLocalRef(commit_type);
    env->DeleteLocalRef(string_type);
    env->DeleteLocalRef(trailer_type);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM*, void*) {
    git_libgit2_init();
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aeibi_avd_core_git_GitNative_initialize(
    JNIEnv* env,
    jobject,
    jstring work_tree_path,
    jstring git_directory_path
) {
    const std::string work_tree = string_from(env, work_tree_path);
    const std::string git_directory = string_from(env, git_directory_path);
    git_repository* repository = nullptr;
    git_config* config = nullptr;
    git_repository_init_options options = GIT_REPOSITORY_INIT_OPTIONS_INIT;
    options.flags =
        GIT_REPOSITORY_INIT_BARE |
        GIT_REPOSITORY_INIT_NO_DOTGIT_DIR |
        GIT_REPOSITORY_INIT_NO_REINIT |
        GIT_REPOSITORY_INIT_MKPATH;
    int result = git_repository_init_ext(&repository, git_directory.c_str(), &options);
    if (result == 0) result = git_repository_set_workdir(repository, work_tree.c_str(), 0);
    if (result == 0) result = git_repository_config(&config, repository);
    if (result == 0) result = git_config_set_string(config, "core.worktree", work_tree.c_str());
    if (result == 0) result = git_config_set_bool(config, "core.bare", 0);
    if (result != 0) throw_native(env, error_kind(result));
    git_config_free(config);
    git_repository_free(repository);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aeibi_avd_core_git_GitNative_commitAll(
    JNIEnv* env,
    jobject,
    jstring git_directory_path,
    jstring message_value,
    jstring author_name_value,
    jstring author_email_value,
    jlong timestamp_epoch_seconds
) {
    const std::string git_directory = string_from(env, git_directory_path);
    const std::string message = string_from(env, message_value);
    const std::string author_name = string_from(env, author_name_value);
    const std::string author_email = string_from(env, author_email_value);
    git_repository* repository = nullptr;
    git_index* index = nullptr;
    git_tree* tree = nullptr;
    git_commit* parent = nullptr;
    git_signature* signature = nullptr;
    git_reference* head = nullptr;
    git_oid tree_oid{};
    git_oid commit_oid{};
    git_strarray paths{nullptr, 0};
    int head_result = 0;
    jstring result_value = nullptr;

    if (!open_repository(env, git_directory, &repository)) goto cleanup;
    if (git_repository_index(&index, repository) != 0) {
        throw_native(env, "OPERATION_FAILED");
        goto cleanup;
    }
    if (git_index_add_all(index, &paths, GIT_INDEX_ADD_DEFAULT, nullptr, nullptr) != 0 ||
        git_index_write(index) != 0 ||
        git_index_write_tree(&tree_oid, index) != 0 ||
        git_tree_lookup(&tree, repository, &tree_oid) != 0) {
        throw_native(env, "OPERATION_FAILED");
        goto cleanup;
    }

    head_result = git_repository_head(&head, repository);
    if (head_result == 0) {
        if (git_commit_lookup(&parent, repository, git_reference_target(head)) != 0) {
            throw_native(env, "OPERATION_FAILED");
            goto cleanup;
        }
        if (git_oid_equal(git_commit_tree_id(parent), &tree_oid)) {
            throw_native(env, "NO_CHANGES");
            goto cleanup;
        }
    } else if (head_result != GIT_EUNBORNBRANCH && head_result != GIT_ENOTFOUND) {
        throw_native(env, "OPERATION_FAILED");
        goto cleanup;
    }

    if (git_signature_new(
            &signature,
            author_name.c_str(),
            author_email.c_str(),
            static_cast<git_time_t>(timestamp_epoch_seconds),
            0
        ) != 0) {
        throw_native(env, "INVALID_INPUT");
        goto cleanup;
    }
    {
        const git_commit* parents[] = {parent};
        const size_t parent_count = parent == nullptr ? 0 : 1;
        if (git_commit_create(
                &commit_oid,
                repository,
                "HEAD",
                signature,
                signature,
                nullptr,
                message.c_str(),
                tree,
                parent_count,
                parent == nullptr ? nullptr : parents
            ) != 0) {
            throw_native(env, "OPERATION_FAILED");
            goto cleanup;
        }
    }
    result_value = string_to(env, oid_string(&commit_oid));

cleanup:
    git_signature_free(signature);
    git_reference_free(head);
    git_commit_free(parent);
    git_tree_free(tree);
    git_index_free(index);
    git_repository_free(repository);
    return result_value;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_aeibi_avd_core_git_GitNative_status(JNIEnv* env, jobject, jstring git_directory_path) {
    const std::string git_directory = string_from(env, git_directory_path);
    git_repository* repository = nullptr;
    git_status_list* statuses = nullptr;
    git_reference* head = nullptr;
    git_status_options options = GIT_STATUS_OPTIONS_INIT;
    jobject result = nullptr;
    if (!open_repository(env, git_directory, &repository)) goto cleanup;
    options.show = GIT_STATUS_SHOW_INDEX_AND_WORKDIR;
    options.flags = GIT_STATUS_OPT_INCLUDE_UNTRACKED | GIT_STATUS_OPT_RECURSE_UNTRACKED_DIRS;
    if (git_status_list_new(&statuses, repository, &options) != 0) {
        throw_native(env, "OPERATION_FAILED");
        goto cleanup;
    }
    {
        int head_result = git_repository_head(&head, repository);
        if (head_result != 0 && head_result != GIT_EUNBORNBRANCH && head_result != GIT_ENOTFOUND) {
            throw_native(env, "OPERATION_FAILED");
            goto cleanup;
        }
        jclass type = env->FindClass("com/aeibi/avd/core/git/NativeGitStatus");
        jmethodID constructor = env->GetMethodID(type, "<init>", "(Ljava/lang/String;Z)V");
        jstring revision = head == nullptr ? nullptr : string_to(env, oid_string(git_reference_target(head)));
        result = env->NewObject(type, constructor, revision, git_status_list_entrycount(statuses) > 0);
        env->DeleteLocalRef(revision);
        env->DeleteLocalRef(type);
    }

cleanup:
    git_reference_free(head);
    git_status_list_free(statuses);
    git_repository_free(repository);
    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_aeibi_avd_core_git_GitNative_readHistory(JNIEnv* env, jobject, jstring git_directory_path) {
    const std::string git_directory = string_from(env, git_directory_path);
    git_repository* repository = nullptr;
    git_revwalk* walk = nullptr;
    jobjectArray result = nullptr;
    std::vector<jobject> commits;
    if (!open_repository(env, git_directory, &repository)) goto cleanup;
    if (git_revwalk_new(&walk, repository) != 0) {
        throw_native(env, "OPERATION_FAILED");
        goto cleanup;
    }
    git_revwalk_sorting(walk, GIT_SORT_TOPOLOGICAL | GIT_SORT_TIME);
    {
        int push_result = git_revwalk_push_head(walk);
        if (push_result == GIT_EUNBORNBRANCH || push_result == GIT_ENOTFOUND) {
            jclass type = env->FindClass("com/aeibi/avd/core/git/NativeGitCommit");
            result = env->NewObjectArray(0, type, nullptr);
            env->DeleteLocalRef(type);
            goto cleanup;
        }
        if (push_result != 0) {
            throw_native(env, "OPERATION_FAILED");
            goto cleanup;
        }
    }
    while (true) {
        git_oid oid{};
        int next_result = git_revwalk_next(&oid, walk);
        if (next_result == GIT_ITEROVER) break;
        if (next_result != 0) {
            throw_native(env, "OPERATION_FAILED");
            goto cleanup;
        }
        git_commit* commit = nullptr;
        if (git_commit_lookup(&commit, repository, &oid) != 0) {
            throw_native(env, "OPERATION_FAILED");
            goto cleanup;
        }
        jobject value = make_commit(env, commit);
        git_commit_free(commit);
        if (value == nullptr || env->ExceptionCheck()) goto cleanup;
        commits.push_back(value);
    }
    {
        jclass type = env->FindClass("com/aeibi/avd/core/git/NativeGitCommit");
        result = env->NewObjectArray(commits.size(), type, nullptr);
        for (size_t index = 0; index < commits.size(); ++index) {
            env->SetObjectArrayElement(result, index, commits[index]);
        }
        env->DeleteLocalRef(type);
    }

cleanup:
    for (jobject commit : commits) env->DeleteLocalRef(commit);
    git_revwalk_free(walk);
    git_repository_free(repository);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aeibi_avd_core_git_GitNative_restoreWorkTree(
    JNIEnv* env,
    jobject,
    jstring git_directory_path,
    jstring revision_value
) {
    const std::string git_directory = string_from(env, git_directory_path);
    const std::string revision = string_from(env, revision_value);
    git_repository* repository = nullptr;
    git_oid oid{};
    git_commit* commit = nullptr;
    if (!open_repository(env, git_directory, &repository)) goto cleanup;
    if (git_oid_fromstr(&oid, revision.c_str()) != 0 ||
        git_commit_lookup(&commit, repository, &oid) != 0) {
        throw_native(env, "INVALID_REVISION");
        goto cleanup;
    }
    {
        git_checkout_options options = GIT_CHECKOUT_OPTIONS_INIT;
        options.checkout_strategy = GIT_CHECKOUT_FORCE | GIT_CHECKOUT_RECREATE_MISSING;
        if (git_checkout_tree(repository, reinterpret_cast<git_object*>(commit), &options) != 0) {
            throw_native(env, "OPERATION_FAILED");
        }
    }

cleanup:
    git_commit_free(commit);
    git_repository_free(repository);
}
