package biz.dealnote.messenger.interactor.impl;

import java.util.ArrayList;
import java.util.List;

import biz.dealnote.messenger.api.interfaces.INetworker;
import biz.dealnote.messenger.api.model.VKApiPhoto;
import biz.dealnote.messenger.api.model.VKApiPost;
import biz.dealnote.messenger.api.model.VKApiUser;
import biz.dealnote.messenger.api.model.VKApiVideo;
import biz.dealnote.messenger.db.column.UserColumns;
import biz.dealnote.messenger.db.interfaces.IRepositories;
import biz.dealnote.messenger.db.model.entity.OwnerEntities;
import biz.dealnote.messenger.db.model.entity.PhotoEntity;
import biz.dealnote.messenger.db.model.entity.PostEntity;
import biz.dealnote.messenger.db.model.entity.UserEntity;
import biz.dealnote.messenger.db.model.entity.VideoEntity;
import biz.dealnote.messenger.interactor.IFaveInteractor;
import biz.dealnote.messenger.interactor.IOwnersInteractor;
import biz.dealnote.messenger.interactor.mappers.Dto2Entity;
import biz.dealnote.messenger.interactor.mappers.Dto2Model;
import biz.dealnote.messenger.interactor.mappers.Entity2Model;
import biz.dealnote.messenger.model.Owner;
import biz.dealnote.messenger.model.Photo;
import biz.dealnote.messenger.model.Post;
import biz.dealnote.messenger.model.User;
import biz.dealnote.messenger.model.Video;
import biz.dealnote.messenger.model.criteria.FavePhotosCriteria;
import biz.dealnote.messenger.model.criteria.FavePostsCriteria;
import biz.dealnote.messenger.model.criteria.FaveVideosCriteria;
import biz.dealnote.messenger.util.VKOwnIds;
import io.reactivex.Completable;
import io.reactivex.Single;

import static biz.dealnote.messenger.util.Objects.nonNull;
import static biz.dealnote.messenger.util.Utils.listEmptyIfNull;
import static biz.dealnote.messenger.util.Utils.safeCountOf;

/**
 * Created by Ruslan Kolbasa on 14.07.2017.
 * phoenix
 */
public class FaveInteractor implements IFaveInteractor {

    private final INetworker networker;
    private final IRepositories cache;
    private final IOwnersInteractor ownersInteractor;

    public FaveInteractor(INetworker networker, IRepositories cache) {
        this.networker = networker;
        this.cache = cache;
        this.ownersInteractor = new OwnersInteractor(networker, cache.owners());
    }

    @Override
    public Single<List<Post>> getPosts(int accountId, int count, int offset) {
        return networker.vkDefault(accountId)
                .fave()
                .getPosts(offset, count, true)
                .flatMap(response -> {
                    List<VKApiPost> dtos = listEmptyIfNull(response.posts);

                    List<Owner> owners = Dto2Model.transformOwners(response.profiles, response.groups);

                    VKOwnIds ids = new VKOwnIds();
                    for(VKApiPost dto : dtos){
                        ids.append(dto);
                    }

                    final OwnerEntities ownerEntities = Dto2Entity.buildOwnerDbos(response.profiles, response.groups);

                    final List<PostEntity> dbos = new ArrayList<>(safeCountOf(response.posts));
                    if(nonNull(response.posts)){
                        for(VKApiPost dto : response.posts){
                            dbos.add(Dto2Entity.buildPostDbo(dto));
                        }
                    }

                    return ownersInteractor.findBaseOwnersDataAsBundle(accountId, ids.getAll(), IOwnersInteractor.MODE_ANY, owners)
                            .map(bundle -> Dto2Model.transformPosts(dtos, bundle))
                            .flatMap(posts -> cache.fave()
                                    .storePosts(accountId, dbos, ownerEntities, offset == 0)
                                    .andThen(Single.just(posts)));
                });
    }

    @Override
    public Single<List<Post>> getCachedPosts(int accountId) {
        return cache.fave().getFavePosts(new FavePostsCriteria(accountId))
                .flatMap(postDbos -> {
                    VKOwnIds ids = new VKOwnIds();
                    for(PostEntity dbo : postDbos){
                        Entity2Model.fillPostOwnerIds(ids, dbo);
                    }

                    return ownersInteractor.findBaseOwnersDataAsBundle(accountId, ids.getAll(), IOwnersInteractor.MODE_ANY)
                            .map(owners -> {
                                List<Post> posts = new ArrayList<>();
                                for(PostEntity dbo : postDbos){
                                    posts.add(Entity2Model.buildPostFromDbo(dbo, owners));
                                }
                                return posts;
                            });
                });
    }

    @Override
    public Single<List<Photo>> getCachedPhotos(int accountId) {
        FavePhotosCriteria criteria = new FavePhotosCriteria(accountId);
        return cache.fave()
                .getPhotos(criteria)
                .map(photoDbos -> {
                    List<Photo> photos = new ArrayList<>(photoDbos.size());
                    for(PhotoEntity dbo : photoDbos){
                        photos.add(Entity2Model.buildPhotoFromDbo(dbo));
                    }
                    return photos;
                });
    }

    @Override
    public Single<List<Photo>> getPhotos(int accountId, int count, int offset) {
        return networker.vkDefault(accountId)
                .fave()
                .getPhotos(offset, count)
                .flatMap(items -> {
                    List<VKApiPhoto> dtos = listEmptyIfNull(items.getItems());

                    List<PhotoEntity> dbos = new ArrayList<>(dtos.size());
                    List<Photo> photos = new ArrayList<>(dtos.size());

                    for(VKApiPhoto dto : dtos){
                        dbos.add(Dto2Entity.buildPhotoDbo(dto));
                        photos.add(Dto2Model.transform(dto));
                    }

                    return cache.fave().storePhotos(accountId, dbos, offset == 0)
                            .map(ints -> photos);
                });
    }

    @Override
    public Single<List<Video>> getCachedVideos(int accountId) {
        FaveVideosCriteria criteria = new FaveVideosCriteria(accountId);

        return cache.fave()
                .getVideos(criteria)
                .map(videoDbos -> {
                    List<Video> videos = new ArrayList<>(videoDbos.size());
                    for(VideoEntity dbo : videoDbos){
                        videos.add(Entity2Model.buildVideoFromDbo(dbo));
                    }
                    return videos;
                });
    }

    @Override
    public Single<List<Video>> getVideos(int accountId, int count, int offset) {
        return networker.vkDefault(accountId)
                .fave()
                .getVideos(offset, count, false)
                .flatMap(items -> {
                    List<VKApiVideo> dtos = listEmptyIfNull(items.getItems());

                    List<VideoEntity> dbos = new ArrayList<>(dtos.size());
                    List<Video> videos = new ArrayList<>(dtos.size());

                    for(VKApiVideo dto : dtos){
                        dbos.add(Dto2Entity.buildVideoDbo(dto));
                        videos.add(Dto2Model.transform(dto));
                    }

                    return cache.fave().storeVideos(accountId, dbos, offset == 0)
                            .map(ints -> videos);
                });
    }

    @Override
    public Single<List<User>> getCachedUsers(int accountId) {
        return cache.fave()
                .getFaveUsers(accountId)
                .map(Entity2Model::buildUsersFromDbo);
    }

    @Override
    public Single<List<User>> getUsers(int accountId, int count, int offset) {
        return networker.vkDefault(accountId)
                .fave()
                .getUsers(offset, count, UserColumns.API_FIELDS)
                .flatMap(items -> {
                    List<VKApiUser> dtos = listEmptyIfNull(items.getItems());
                    List<UserEntity> dbos = Dto2Entity.buildUserDbos(dtos);

                    return cache.fave()
                            .storeUsers(accountId, dbos, offset == 0)
                            .andThen(Single.just(Dto2Model.transformUsers(dtos)));
                });
    }

    @Override
    public Completable addUser(int accountId, int userId) {
        return networker.vkDefault(accountId)
                .fave()
                .addUser(userId)
                .toCompletable();
    }
}